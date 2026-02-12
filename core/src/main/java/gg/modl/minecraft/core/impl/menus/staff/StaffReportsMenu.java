package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Staff Reports Menu - displays all open reports.
 */
public class StaffReportsMenu extends BaseStaffListMenu<StaffReportsMenu.Report> {

    public static class Report {
        private final String id;
        private final String type;
        private final String reporterName;
        private final UUID reportedPlayerUuid;
        private final String reportedPlayerName;
        private final String content;
        private final Date date;
        private final String status;

        public Report(String id, String type, String reporterName, UUID reportedPlayerUuid,
                      String reportedPlayerName, String content, Date date, String status) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.reportedPlayerUuid = reportedPlayerUuid;
            this.reportedPlayerName = reportedPlayerName;
            this.content = content;
            this.date = date;
            this.status = status;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getReporterName() { return reporterName; }
        public UUID getReportedPlayerUuid() { return reportedPlayerUuid; }
        public String getReportedPlayerName() { return reportedPlayerName; }
        public String getContent() { return content; }
        public Date getDate() { return date; }
        public String getStatus() { return status; }
    }

    private List<Report> reports = new ArrayList<>();
    private String currentFilter = "all";
    private String currentStatusFilter = "open";
    private final List<String> filterOptions = Arrays.asList("all", "player", "chat", "cheating", "behavior", "other");
    private final String panelUrl;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new staff reports menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param panelUrl The panel URL
     * @param backAction Action to return to parent menu
     */
    public StaffReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                            boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Reports", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        this.parentBackAction = backAction;
        activeTab = StaffTab.REPORTS;

        // Fetch reports from API
        fetchReports();
    }

    private void fetchReports() {
        try {
            httpClient.getReports("all").thenAccept(response -> {
                if (response.isSuccess() && response.getReports() != null) {
                    reports.clear();
                    for (var report : response.getReports()) {
                        UUID reportedUuid = null;
                        try {
                            if (report.getReportedPlayerUuid() != null) {
                                reportedUuid = UUID.fromString(report.getReportedPlayerUuid());
                            }
                        } catch (Exception ignored) {}

                        reports.add(new Report(
                                report.getId(),
                                report.getType() != null ? report.getType() : report.getCategory(),
                                report.getReporterName(),
                                reportedUuid,
                                report.getReportedPlayerName(),
                                report.getContent() != null ? report.getContent() : report.getSubject(),
                                report.getCreatedAt(),
                                report.getStatus()
                        ));
                    }
                }
            }).join();
        } catch (Exception e) {
            e.printStackTrace();
            // Failed to fetch - list remains empty
        }
    }

    /**
     * Set the current filter (used when cycling filters).
     */
    public StaffReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    /**
     * Set the current status filter (open/closed).
     */
    public StaffReportsMenu withStatusFilter(String statusFilter) {
        this.currentStatusFilter = statusFilter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add filter button at slot 40 (y position in navigation row)
        // Note: actionHandler("filter") is already set in MenuItems.filterButton()
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions, currentStatusFilter, "reports"));

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (reports.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null, null, null));
        }

        // Filter and sort reports (newest first)
        List<Report> filtered = new ArrayList<>();

        for (Report report : reports) {
            boolean typeMatch = currentFilter.equals("all") || (report.getType() != null && report.getType().equalsIgnoreCase(currentFilter));
            boolean statusMatch = "open".equalsIgnoreCase(currentStatusFilter)
                    ? !"closed".equalsIgnoreCase(report.getStatus())
                    : "closed".equalsIgnoreCase(report.getStatus());
            if (typeMatch && statusMatch) {
                filtered.add(report);
            }
        }

        // If filtering results in empty list, return placeholder
        if (filtered.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null, null, null));
        }

        filtered.sort((r1, r2) -> {
            if (r1.getDate() == null && r2.getDate() == null) return 0;
            if (r1.getDate() == null) return 1;
            if (r2.getDate() == null) return -1;
            return r2.getDate().compareTo(r1.getDate());
        });
        return filtered;
    }

    @Override
    protected CirrusItem map(Report report) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (report.getId() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.staff_reports"));
        }

        // Build variables map
        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", report.getType() != null ? report.getType() : "Unknown");
        vars.put("reporter", report.getReporterName() != null ? report.getReporterName() : "Unknown");
        vars.put("reported", report.getReportedPlayerName() != null ? report.getReportedPlayerName() : "Unknown");
        vars.put("date", MenuItems.formatDate(report.getDate()));
        vars.put("status", report.getStatus() != null && report.getStatus().equalsIgnoreCase("closed")
                ? "&cCLOSED" : "&aOPEN");

        // Content - normalize newlines, strip markdown, and wrap text
        String content = report.getContent() != null ? report.getContent() : "";
        // Handle literal \n sequences and actual newlines
        content = content.replace("\\n", "\n");
        content = content.replace("**", "").replace("```", "");
        List<String> wrappedContent = new ArrayList<>();
        for (String paragraph : content.split("\n")) {
            if (paragraph.trim().isEmpty()) {
                wrappedContent.add("");
            } else {
                wrappedContent.addAll(MenuItems.wrapText(paragraph.trim(), 7));
            }
        }
        vars.put("content", String.join("\n", wrappedContent));

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.staff_report_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Handle {content} which may contain newlines
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

        // Get title from locale
        String title = locale.getMessage("menus.staff_report_item.title", vars);

        CirrusItemType itemType = getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private CirrusItemType getReportItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        switch (type.toLowerCase()) {
            case "player":
                return CirrusItemType.PLAYER_HEAD;
            case "chat":
                return CirrusItemType.WRITABLE_BOOK;
            case "cheating":
                return CirrusItemType.DIAMOND_SWORD;
            case "behavior":
                return CirrusItemType.SKELETON_SKULL;
            default:
                return CirrusItemType.PAPER;
        }
    }

    @Override
    protected void handleClick(Click click, Report report) {
        // Handle placeholder - do nothing
        if (report.getId() == null) {
            return;
        }

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            // Right-click: dismiss report
            dismissReport(click, report);
        } else {
            // Left-click: inspect player
            inspectPlayer(click, report);
        }
    }

    private void dismissReport(Click click, Report report) {
        sendMessage(MenuItems.COLOR_YELLOW + "Dismissing report...");

        httpClient.dismissReport(report.getId(), viewerName, "Insufficient evidence").thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Report dismissed.");

            // Refresh menu
            StaffReportsMenu refreshed = new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                    .withFilter(currentFilter).withStatusFilter(currentStatusFilter);
            platform.runOnMainThread(() -> ActionHandlers.openMenu(refreshed).handle(click));
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to dismiss report: " + e.getMessage());
            return null;
        });
    }

    private void inspectPlayer(Click click, Report report) {
        if (report.getReportedPlayerUuid() == null) {
            sendMessage(MenuItems.COLOR_RED + "Cannot inspect: no player UUID for this report.");
            return;
        }

        click.clickedMenu().close();

        // Fetch player profile and open inspect menu
        httpClient.getPlayerProfile(report.getReportedPlayerUuid()).thenAccept(response -> {
            if (response.getStatus() == 200) {
                platform.runOnMainThread(() -> {
                    // Pass back action to return to reports menu
                    new InspectMenu(platform, httpClient, viewerUuid, viewerName, response.getProfile(),
                            p -> new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p))
                            .display(click.player());
                });
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

        // Filter handler
        registerActionHandler("filter", this::handleFilter);

        // Override header navigation - primary tabs should NOT pass backAction
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", click -> {
            // Already here, do nothing
        });

        registerActionHandler("openPunishments", ActionHandlers.openMenu(
                new RecentPunishmentsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openTickets", ActionHandlers.openMenu(
                new TicketsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openPanel", click -> {
            click.clickedMenu().close();
            String escapedUrl = panelUrl.replace("\"", "\\\"");
            String panelJson = String.format(
                "{\"text\":\"\",\"extra\":[" +
                "{\"text\":\"Staff Panel: \",\"color\":\"gold\"}," +
                "{\"text\":\"%s\",\"color\":\"aqua\",\"underlined\":true," +
                "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%s\"}," +
                "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click to open in browser\"}}]}",
                escapedUrl, panelUrl
            );
            platform.sendJsonMessage(viewerUuid, panelJson);
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }

    private void handleFilter(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            // Toggle status filter between open and closed
            String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
            ActionHandlers.openMenu(
                    new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                            .withFilter(currentFilter)
                            .withStatusFilter(newStatus))
                    .handle(click);
        } else {
            // Cycle through type filter options
            int currentIndex = filterOptions.indexOf(currentFilter);
            int nextIndex = (currentIndex + 1) % filterOptions.size();
            String newFilter = filterOptions.get(nextIndex);

            ActionHandlers.openMenu(
                    new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                            .withFilter(newFilter)
                            .withStatusFilter(currentStatusFilter))
                    .handle(click);
        }
    }
}
