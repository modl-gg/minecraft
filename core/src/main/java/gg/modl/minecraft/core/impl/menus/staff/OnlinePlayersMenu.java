package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.OnlinePlayersResponse;
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;

public class OnlinePlayersMenu extends BaseStaffListMenu<OnlinePlayersMenu.OnlinePlayer> {
    @Getter
    public static class ReportSummary {
        private final String details, reporter;
        private final Date date;

        public ReportSummary(String details, String reporter, Date date) {
            this.details = details;
            this.reporter = reporter;
            this.date = date;
        }
    }

    public static class OnlinePlayer {
        @Getter private final UUID uuid;
        @Getter private final String name;
        private final long sessionStartTime;
        @Getter private final long totalPlaytime;
        @Getter private final int punishmentCount;
        @Getter private List<ReportSummary> recentReports = new ArrayList<>();

        public OnlinePlayer(UUID uuid, String name, long sessionStartTime, long totalPlaytime, int punishmentCount) {
            this.uuid = uuid;
            this.name = name;
            this.sessionStartTime = sessionStartTime;
            this.totalPlaytime = totalPlaytime;
            this.punishmentCount = punishmentCount;
        }

        public long getSessionDuration() { return System.currentTimeMillis() - sessionStartTime; }
        public int getRecentReportCount() { return recentReports.size(); }
    }

    private List<OnlinePlayer> onlinePlayers = new ArrayList<>();
    private String currentSort;
    private final List<String> sortOptions = Arrays.asList("Least Playtime", "Recent Gameplay Reports", "Longest Session");
    private final String panelUrl;

    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction, "Recent Gameplay Reports", null);
    }

    public OnlinePlayersMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                             boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction,
                             String sortOption, List<OnlinePlayer> existingPlayers) {
        super("Online Players", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.currentSort = sortOption;
        activeTab = StaffTab.ONLINE_PLAYERS;

        if (existingPlayers != null) {
            this.onlinePlayers = new ArrayList<>(existingPlayers);
        } else {
            fetchOnlinePlayers();
        }
    }

    private void fetchOnlinePlayers() {
        try {
            httpClient.getOnlinePlayers().thenAccept(response -> {
                if (response.isSuccess() && response.getPlayers() != null) {
                    onlinePlayers.clear();
                    for (OnlinePlayersResponse.OnlinePlayer player : response.getPlayers()) {
                        UUID uuid = null;
                        try {
                            uuid = UUID.fromString(player.getUuid());
                        } catch (Exception ignored) {}

                        long sessionStart = player.getJoinedAt() != null ? player.getJoinedAt().getTime() : System.currentTimeMillis();
                        long totalPlaytime = player.getTotalPlaytimeMs() != null ? player.getTotalPlaytimeMs() : 0;
                        onlinePlayers.add(new OnlinePlayer(uuid, player.getUsername(), sessionStart, totalPlaytime, 0));
                    }
                }
            }).join();
        } catch (Exception ignored) {}

        fetchReportData();
    }

    private void fetchReportData() {
        try {
            httpClient.getReports("open").thenAccept(response -> {
                if (!response.isSuccess() || response.getReports() == null) return;

                Map<String, List<ReportSummary>> reportsByPlayer = new HashMap<>();
                for (ReportsResponse.Report report : response.getReports()) {
                    String type = report.getType() != null ? report.getType() : report.getCategory();
                    if ("player".equalsIgnoreCase(type)) type = "gameplay";
                    if (!"gameplay".equalsIgnoreCase(type)) continue;

                    String uuid = report.getReportedPlayerUuid();
                    if (uuid == null) continue;

                    String details = extractDetails(report.getContent(), report.getSubject());
                    String reporter = report.getReporterName() != null ? report.getReporterName() : "Unknown";
                    reportsByPlayer.computeIfAbsent(uuid, k -> new ArrayList<>())
                            .add(new ReportSummary(details, reporter, report.getCreatedAt()));
                }

                for (OnlinePlayer player : onlinePlayers) {
                    if (player.getUuid() == null) continue;
                    List<ReportSummary> reports = reportsByPlayer.get(player.getUuid().toString());
                    if (reports == null) continue;

                    reports.sort((a, b) -> {
                        if (a.getDate() == null && b.getDate() == null) return 0;
                        if (a.getDate() == null) return 1;
                        if (b.getDate() == null) return -1;
                        return b.getDate().compareTo(a.getDate());
                    });
                    player.recentReports = reports;
                }
            }).join();
        } catch (Exception ignored) {}
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.SORT_BUTTON, MenuItems.sortButton(currentSort, sortOptions)
                .actionHandler("sort"));

        return items;
    }

    @Override
    protected Collection<OnlinePlayer> elements() {
        if (onlinePlayers.isEmpty())
            return Collections.singletonList(new OnlinePlayer(null, null, 0, 0, 0));

        List<OnlinePlayer> sorted = new ArrayList<>(onlinePlayers);

        switch (currentSort) {
            case "Least Playtime":
                sorted.sort(Comparator.comparingLong(OnlinePlayer::getTotalPlaytime));
                break;
            case "Longest Session":
                sorted.sort((p1, p2) -> Long.compare(p2.getSessionDuration(), p1.getSessionDuration()));
                break;
            case "Recent Gameplay Reports":
            default:
                sorted.sort((p1, p2) -> Integer.compare(p2.getRecentReportCount(), p1.getRecentReportCount()));
                break;
        }

        return sorted;
    }

    @Override
    protected CirrusItem map(OnlinePlayer player) {
        if (player.getName() == null)
            return createEmptyPlaceholder("No online players");

        LocaleManager localeManager = platform.getLocaleManager();

        String punishments = player.getPunishmentCount() > 0
                ? "&c" + player.getPunishmentCount()
                : "&a0";

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player_name", player.getName());
        placeholders.put("session_duration", MenuItems.formatDuration(player.getSessionDuration()));
        placeholders.put("total_playtime", MenuItems.formatDuration(player.getTotalPlaytime()));
        placeholders.put("punishments", punishments);
        placeholders.put("report_count", String.valueOf(player.getRecentReportCount()));

        String title = localeManager.getMessage("menus.online_players.title", placeholders);
        List<String> templateLore = localeManager.getMessageList("menus.online_players.lore", placeholders);

        List<String> lore = new ArrayList<>();
        for (String line : templateLore) {
            if (line.contains("{report-list}")) {
                List<ReportSummary> reports = player.getRecentReports();
                if (reports.isEmpty()) {
                    lore.add(localeManager.getMessage("menus.online_players.no_reports"));
                } else {
                    int shown = Math.min(reports.size(), 5);
                    for (int i = 0; i < shown; i++) {
                        ReportSummary report = reports.get(i);
                        Map<String, String> reportVars = new HashMap<>();
                        reportVars.put("date", MenuItems.formatDate(report.getDate()));
                        reportVars.put("details", report.getDetails());
                        reportVars.put("reporter", report.getReporter());
                        lore.add(localeManager.getMessage("menus.online_players.report_entry", reportVars));
                    }
                    if (reports.size() > 5) {
                        lore.add(localeManager.getMessage("menus.online_players.reports_more",
                                Map.of("count", String.valueOf(reports.size() - 5))));
                    }
                }
            } else {
                lore.add(line);
            }
        }

        CirrusItem headItem = CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.translateColorCodes(title)),
                MenuItems.lore(lore)
        );

        if (player.getUuid() != null && platform.getCache() != null) {
            String cachedTexture = platform.getCache().getSkinTexture(player.getUuid());
            if (cachedTexture != null) {
                headItem = headItem.texture(cachedTexture);
            } else {
                final UUID uuid = player.getUuid();
                WebPlayer.get(uuid).thenAccept(wp -> {
                    if (wp != null && wp.isValid() && wp.getTextureValue() != null) {
                        platform.getCache().cacheSkinTexture(uuid, wp.getTextureValue());
                    }
                });
            }
        }

        return headItem;
    }

    @Override
    protected void handleClick(Click click, OnlinePlayer player) {
        if (player.getName() == null) return;

        StaffModeService staffModeService = platform.getStaffModeService();
        if (staffModeService != null && click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            if (platform.getCache() == null || !platform.getCache().hasPermission(viewerUuid, Permissions.MOD_ACTIONS)) {
                sendMessage(platform.getLocaleManager().getMessage("general.no_permission"));
                return;
            }

            if (player.getUuid() != null && player.getUuid().equals(viewerUuid)) {
                sendMessage(MenuItems.COLOR_RED + "You cannot target yourself");
                return;
            }

            click.clickedMenu().close();

            if (!staffModeService.isInStaffMode(viewerUuid)) {
                staffModeService.enable(viewerUuid);
                AbstractPlayer staffPlayer = platform.getPlayer(viewerUuid);
                String inGameName = staffPlayer != null ? staffPlayer.getName() : "Staff";
                String panelName = platform.getCache() != null ? platform.getCache().getStaffDisplayName(viewerUuid) : null;
                if (panelName == null) panelName = inGameName;
                platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("staff_mode.enabled"));
                platform.staffBroadcast(platform.getLocaleManager().getMessage("staff_mode.enabled_broadcast", Map.of(
                        "staff", panelName, "in-game-name", inGameName)));
                BridgeService bridgeService = platform.getBridgeService();
                if (bridgeService != null) {
                    bridgeService.sendStaffModeEnter(viewerUuid.toString(), inGameName, panelName);
                }
            }

            staffModeService.setTarget(viewerUuid, player.getUuid());
            BridgeService bridgeService = platform.getBridgeService();
            if (bridgeService != null) {
                bridgeService.sendTargetRequest(viewerUuid.toString(), player.getUuid().toString());
            }
            sendMessage(MenuItems.COLOR_GREEN + "Now targeting " + MenuItems.COLOR_GOLD + player.getName());
            return;
        }

        click.clickedMenu().close();

        final String sortState = currentSort;
        final List<OnlinePlayer> playerState = new ArrayList<>(onlinePlayers);

        httpClient.getPlayerProfile(player.getUuid()).thenAccept(response -> {
            if (response.getStatus() == 200) {
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, response.getProfile(),
                    p -> new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null,
                        sortState, playerState).display(p))
                    .display(click.player());
            } else {
                sendMessage(MenuItems.COLOR_RED + "Failed to load player profile");
            }
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to load player profile: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("sort", this::handleSort);

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openOnlinePlayers", click -> {});
    }

    private static String extractDetails(String content, String subject) {
        String first = cleanLine(content != null ? content : subject);
        if ("Automated anticheat report.".equals(first) && content != null) {
            String third = nthLine(content, 3);
            if (third != null) return third;
        }
        return first;
    }

    private static String nthLine(String text, int n) {
        String[] lines = text.split("\n");
        for (int i = 0, found = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty() && ++found == n) return cleanLine(trimmed);
        }
        return null;
    }

    private static String cleanLine(String text) {
        if (text == null || text.isEmpty()) return "";
        int idx = text.indexOf('\n');
        String line = idx >= 0 ? text.substring(0, idx).trim() : text.trim();
        return line.replace("**", "").replace("__", "").replace("~~", "");
    }

    private void handleSort(Click click) {
        int currentIndex = sortOptions.indexOf(currentSort);
        int nextIndex = (currentIndex + 1) % sortOptions.size();
        String nextSort = sortOptions.get(nextIndex);

        ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction,
                        nextSort, onlinePlayers))
                .handle(click);
    }
}
