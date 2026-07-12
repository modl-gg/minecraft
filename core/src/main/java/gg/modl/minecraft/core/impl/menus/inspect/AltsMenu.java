package gg.modl.minecraft.core.impl.menus.inspect;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource.FetchResult;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.SkinTextureCache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Getter;

public class AltsMenu extends BaseInspectListMenu<Account> {
    private static final Logger logger = Logger.getLogger(AltsMenu.class.getName());
    private static final int PAGE_SIZE = 7;
    private static final int INITIAL_LOAD_PAGES = 2;

    private final PaginatedDataSource<Account> dataSource;
    private int pageRefreshRequest;
    @Getter private final CompletableFuture<Void> dataFuture;

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("Alts: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.ALTS;

        dataSource = buildDataSource();
        this.dataFuture = loadLinkedAccounts();
    }

    private AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext,
                     List<Account> preloaded, int totalCount) {
        super("Alts: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.ALTS;

        dataSource = buildDataSource();
        dataSource.initialize(preloaded, totalCount);
        this.dataFuture = CompletableFuture.completedFuture(null);
    }

    private PaginatedDataSource<Account> buildDataSource() {
        return new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<FetchResult<Account>> future = new CompletableFuture<>();
            httpClient.getLinkedAccounts(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    cacheSkinTextures(response.getLinkedAccounts());
                    future.complete(new FetchResult<>(response.getLinkedAccounts(), response.getTotalCount()));
                } else {
                    future.complete(new FetchResult<>(listOf(), 0, false));
                }
            }).exceptionally(e -> {
                future.complete(new FetchResult<>(listOf(), 0, false));
                return null;
            });
            return future;
        });
    }

    private CompletableFuture<Void> loadLinkedAccounts() {
        return httpClient.getLinkedAccounts(targetUuid, 1, PAGE_SIZE * INITIAL_LOAD_PAGES).thenAccept(response -> {
            if (response.getStatus() == 200) {
                List<Account> initialAccounts = new ArrayList<>(response.getLinkedAccounts());
                cacheSkinTextures(initialAccounts);
                dataSource.initialize(initialAccounts, response.getTotalCount());
            }
        }).exceptionally(e -> {
            logger.log(Level.WARNING, "Failed to fetch linked accounts for " + targetUuid, e);
            return null;
        });
    }

    private void cacheSkinTextures(List<Account> accounts) {
        for (Account alt : accounts) {
            if (alt.getMinecraftUuid() != null && PluginServices.cache().getSkinTexture(alt.getMinecraftUuid()) == null) {
                final UUID altUuid = alt.getMinecraftUuid();
                MojangProfiles.client().get(altUuid).thenAccept(wp -> {
                    if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                        PluginServices.cache().cacheSkinTexture(altUuid, wp.getTextureValue());
                    }
                });
            }
        }
    }

    @Override
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            int refreshRequest = ++pageRefreshRequest;
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / PAGE_SIZE + 1, () -> {
                if (refreshRequest != pageRefreshRequest) return;
                AltsMenu newMenu = new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext,
                        dataSource.getAllLoadedItems(), dataSource.getTotalCount());
                newMenu.setInitialPage(nextPage);
                MenuAsync.displayWhenLoaded(platform, newMenu.getDataFuture(), click.player(), newMenu::display);
            });
            return true;
        }
        dataSource.prefetchIfNeeded(nextPage);
        return false;
    }

    @Override
    public boolean hasNextPage() {
        return currentPageIndex().get() < dataSource.getTotalMenuPages() - 1;
    }

    @Override
    protected Collection<Account> elements() {
        List<Account> accounts = dataSource.getAllLoadedItems();
        if (accounts.isEmpty())
            return Collections.singletonList(new Account(null, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()));
        return accounts;
    }

    @Override
    protected CirrusItem map(Account alt) {
        LocaleManager locale = PluginServices.locale();

        if (alt.getMinecraftUuid() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.alts"));
        }

        String altName = getPlayerName(alt);

        List<Punishment> activePunishments = alt.getPunishments().stream()
                .filter(Punishment::isActive)
                .collect(Collectors.toList());
        long activeCount = activePunishments.size();
        long inactiveCount = alt.getPunishments().size() - activeCount;

        boolean isBanned = activePunishments.stream().anyMatch(Punishment::isBanType);
        boolean isMuted = activePunishments.stream().anyMatch(Punishment::isMuteType);
        String color;
        if (isBanned)
            color = "&c";
        else if (isMuted)
            color = "&e";
        else
            color = "&a";

        boolean isOnline = PluginServices.cache() != null && PluginServices.cache().getPlayerProfile(alt.getMinecraftUuid()) != null;
        boolean realIpLogged = !alt.getIpList().isEmpty();

        String firstLogin = "Unknown";
        if (!alt.getUsernames().isEmpty()) {
            Date earliest = alt.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
            if (earliest != null) firstLogin = MenuItems.formatDate(earliest);
        }

        String lastSeenOrSessionTime = "N/A";
        if (!alt.getUsernames().isEmpty()) {
            Date latest = alt.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(null);
            if (latest != null) lastSeenOrSessionTime = MenuItems.formatDate(latest);
        }

        StringBuilder punishmentsBuilder = new StringBuilder();
        if (activeCount > 0) {
            List<String> activeLines = locale.getMessageList("menus.alt_item.punishment_line_active_true");
            String activeFormat = locale.getMessage("menus.alt_item.active_punishment_format");
            StringBuilder activePunishmentList = new StringBuilder();
            for (int i = 0; i < activePunishments.size(); i++) {
                Punishment p = activePunishments.get(i);
                String pId = p.getId();
                String pDate = MenuItems.formatDate(p.getIssued());
                String pType = p.getTypeCategory();
                String pRemaining = "Permanent";
                Date effectiveExpiry = p.getEffectiveExpiry();
                if (effectiveExpiry != null) {
                    long remaining = effectiveExpiry.getTime() - System.currentTimeMillis();
                    pRemaining = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                }
                String formattedPunishment = activeFormat
                        .replace("{punishment_id}", pId)
                        .replace("{punishment_date}", pDate)
                        .replace("{punishment_type}", pType)
                        .replace("{punishment_remaining}", pRemaining);
                if (i > 0)
                    activePunishmentList.append("\n");
                activePunishmentList.append(formattedPunishment);
            }

            for (int i = 0; i < activeLines.size(); i++) {
                String line = activeLines.get(i)
                        .replace("{active_count}", String.valueOf(activeCount))
                        .replace("{active_punishment_list}", activePunishmentList.toString());
                if (i > 0)
                    punishmentsBuilder.append("\n");
                punishmentsBuilder.append(line);
            }
        } else {
            List<String> inactiveLines = locale.getMessageList("menus.alt_item.punishment_line_active_false");
            for (int i = 0; i < inactiveLines.size(); i++) {
                String line = inactiveLines.get(i)
                        .replace("{inactive_count}", String.valueOf(inactiveCount));
                if (i > 0)
                    punishmentsBuilder.append("\n");
                punishmentsBuilder.append(line);
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("color", color);
        vars.put("player_name", altName);
        vars.put("uuid", alt.getMinecraftUuid().toString());
        vars.put("is_online", isOnline ? "&aYes" : "&cNo");
        vars.put("last_seen_or_session_time", lastSeenOrSessionTime);
        String server = "Unknown";
        if (isOnline) {
            String playerServer = platform.getPlayerServer(alt.getMinecraftUuid());
            if (playerServer != null) server = playerServer;
        }
        vars.put("server", server);
        vars.put("real_ip", realIpLogged ? "&aYes" : "&cNo");
        vars.put("first_login", firstLogin);
        vars.put("punishments", punishmentsBuilder.toString());

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.alt_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else
                lore.add(processed);
        }

        String title = locale.getMessage("menus.alt_item.title", vars);

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );

        headItem = SkinTextureCache.applyCached(headItem, alt.getMinecraftUuid());

        return headItem;
    }

    @Override
    protected void handleClick(Click click, Account alt) {
        if (alt.getMinecraftUuid() == null) return;

        Consumer<CirrusPlayerWrapper> backToAlts = player -> {
            AltsMenu m = new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
            MenuAsync.displayWhenLoaded(platform, m.getDataFuture(), player, m::display);
        };

        ActionHandlers.openMenu(
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, alt, backToAlts))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        registerActionHandler("openAlts", click -> {});
    }
}
