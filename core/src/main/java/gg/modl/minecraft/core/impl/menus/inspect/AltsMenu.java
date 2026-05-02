package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.LinkedAccountsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.WebPlayer;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AltsMenu extends BaseInspectListMenu<Account> {
    private static final Logger logger = Logger.getLogger(AltsMenu.class.getName());
    private static final int PAGE_SIZE = 7;
    private static final int INITIAL_LOAD_PAGES = 2;

    private final PaginatedDataSource<Account> dataSource;

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("Alts: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.ALTS;

        dataSource = new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<PaginatedDataSource.FetchResult<Account>> future = new CompletableFuture<>();
            httpClient.getLinkedAccounts(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    cacheSkinTextures(response.getLinkedAccounts());
                    future.complete(new PaginatedDataSource.FetchResult<>(response.getLinkedAccounts(), response.getTotalCount()));
                } else {
                    future.complete(new PaginatedDataSource.FetchResult<>(listOf(), 0));
                }
            }).exceptionally(e -> {
                future.complete(new PaginatedDataSource.FetchResult<>(listOf(), 0));
                return null;
            });
            return future;
        });

        loadLinkedAccounts();
    }

    private void loadLinkedAccounts() {
        try {
            LinkedAccountsResponse response = httpClient.getLinkedAccounts(targetUuid, 1, PAGE_SIZE * INITIAL_LOAD_PAGES).join();
            if (response.getStatus() == 200) {
                List<Account> initialAccounts = new ArrayList<>(response.getLinkedAccounts());
                cacheSkinTextures(initialAccounts);
                dataSource.initialize(initialAccounts, response.getTotalCount());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch linked accounts for " + targetUuid, e);
        }
    }

    private void cacheSkinTextures(List<Account> accounts) {
        if (platform.getCache() == null) return;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Account alt : accounts) {
            if (alt.getMinecraftUuid() != null && platform.getCache().getSkinTexture(alt.getMinecraftUuid()) == null) {
                final UUID altUuid = alt.getMinecraftUuid();
                futures.add(WebPlayer.get(altUuid).thenAccept(wp -> {
                    if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                        platform.getCache().cacheSkinTexture(altUuid, wp.getTextureValue());
                    }
                }));
            }
        }
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            dataSource.setOnDataLoaded(() -> {
                AltsMenu newMenu = new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
                newMenu.dataSource.initialize(dataSource.getAllLoadedItems(), dataSource.getTotalCount());
                newMenu.display(click.player());
                newMenu.setInitialPage(nextPage);
                newMenu.display(click.player());
            });
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / PAGE_SIZE + 1);
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
        LocaleManager locale = platform.getLocaleManager();

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

        boolean isOnline = platform.getCache() != null && platform.getCache().getPlayerProfile(alt.getMinecraftUuid()) != null;
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
                Long duration = p.getDuration();
                if (duration != null && duration > 0 && p.getStarted() != null) {
                    long expiryTime = p.getStarted().getTime() + duration;
                    long remaining = expiryTime - System.currentTimeMillis();
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

        if (platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(alt.getMinecraftUuid());
            if (cachedTexture != null) headItem = headItem.texture(cachedTexture);
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, Account alt) {
        if (alt.getMinecraftUuid() == null) return;

        Consumer<CirrusPlayerWrapper> backToAlts = player ->
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext)
                        .display(player);

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
