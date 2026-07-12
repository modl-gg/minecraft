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
import gg.modl.minecraft.core.impl.menus.base.PaginatedInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource.FetchResult;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.SkinTextureCache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import java.util.ArrayList;
import java.util.Arrays;
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

public class AltsMenu extends PaginatedInspectListMenu<Account> {
    private static final Logger logger = Logger.getLogger(AltsMenu.class.getName());
    private static final int PAGE_SIZE = 7;
    private static final int INITIAL_LOAD_PAGES = 2;

    @Getter private final CompletableFuture<Void> dataFuture;

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                    Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("Alts: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext, PAGE_SIZE);
        activeTab = InspectTab.ALTS;

        dataSource = buildDataSource();
        this.dataFuture = loadLinkedAccounts();
    }

    private AltsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext,
                     List<Account> preloaded, int totalCount) {
        super("Alts: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext, PAGE_SIZE);
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
    protected void openLoadedPage(Click click, int nextPage) {
        AltsMenu newMenu = new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext,
                dataSource.getAllLoadedItems(), dataSource.getTotalCount());
        newMenu.setInitialPage(nextPage);
        MenuAsync.displayWhenLoaded(platform, newMenu.getDataFuture(), click.player(), newMenu::display);
    }

    @Override
    protected Account emptyElement() {
        return new Account(null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    @Override
    protected CirrusItem map(Account alt) {
        LocaleManager locale = PluginServices.locale();

        if (alt.getMinecraftUuid() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.alts"));
        }

        Map<String, String> vars = buildAltVars(alt, locale);

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

        return SkinTextureCache.applyCached(headItem, alt.getMinecraftUuid());
    }

    private Map<String, String> buildAltVars(Account alt, LocaleManager locale) {
        List<Punishment> activePunishments = alt.getPunishments().stream()
                .filter(Punishment::isActive)
                .collect(Collectors.toList());
        long activeCount = activePunishments.size();
        long inactiveCount = alt.getPunishments().size() - activeCount;

        boolean isOnline = PluginServices.cache() != null && PluginServices.cache().getPlayerProfile(alt.getMinecraftUuid()) != null;

        Map<String, String> vars = new HashMap<>();
        vars.put("color", resolveStatusColor(activePunishments));
        vars.put("player_name", getPlayerName(alt));
        vars.put("uuid", alt.getMinecraftUuid().toString());
        vars.put("is_online", isOnline ? "&aYes" : "&cNo");
        vars.put("last_seen_or_session_time", resolveLatestSeen(alt));
        vars.put("server", resolveServer(alt, isOnline));
        vars.put("real_ip", !alt.getIpList().isEmpty() ? "&aYes" : "&cNo");
        vars.put("first_login", resolveFirstLogin(alt));
        vars.put("punishments", buildPunishmentsSummary(locale, activePunishments, activeCount, inactiveCount));
        return vars;
    }

    private static String resolveStatusColor(List<Punishment> activePunishments) {
        if (activePunishments.stream().anyMatch(Punishment::isBanType)) return "&c";
        if (activePunishments.stream().anyMatch(Punishment::isMuteType)) return "&e";
        return "&a";
    }

    private String resolveServer(Account alt, boolean isOnline) {
        if (isOnline) {
            String playerServer = platform.getPlayerServer(alt.getMinecraftUuid());
            if (playerServer != null) return playerServer;
        }
        return "Unknown";
    }

    private static String resolveFirstLogin(Account alt) {
        if (alt.getUsernames().isEmpty()) return "Unknown";
        Date earliest = alt.getUsernames().stream()
                .map(Account.Username::getDate)
                .filter(Objects::nonNull)
                .min(Date::compareTo)
                .orElse(null);
        return earliest != null ? MenuItems.formatDate(earliest) : "Unknown";
    }

    private static String resolveLatestSeen(Account alt) {
        if (alt.getUsernames().isEmpty()) return "N/A";
        Date latest = alt.getUsernames().stream()
                .map(Account.Username::getDate)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);
        return latest != null ? MenuItems.formatDate(latest) : "N/A";
    }

    private static String buildPunishmentsSummary(LocaleManager locale, List<Punishment> activePunishments,
                                                  long activeCount, long inactiveCount) {
        StringBuilder punishmentsBuilder = new StringBuilder();
        if (activeCount > 0) {
            List<String> activeLines = locale.getMessageList("menus.alt_item.punishment_line_active_true");
            String activeFormat = locale.getMessage("menus.alt_item.active_punishment_format");
            StringBuilder activePunishmentList = new StringBuilder();
            for (int i = 0; i < activePunishments.size(); i++) {
                Punishment p = activePunishments.get(i);
                String pRemaining = "Permanent";
                Date effectiveExpiry = p.getEffectiveExpiry();
                if (effectiveExpiry != null) {
                    long remaining = effectiveExpiry.getTime() - System.currentTimeMillis();
                    pRemaining = MenuItems.formatDuration(remaining > 0 ? remaining : 0);
                }
                String formattedPunishment = activeFormat
                        .replace("{punishment_id}", p.getId())
                        .replace("{punishment_date}", MenuItems.formatDate(p.getIssued()))
                        .replace("{punishment_type}", p.getTypeCategory())
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
        return punishmentsBuilder.toString();
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

        registerActionHandler("openAlts", click -> {});
    }
}
