package gg.modl.minecraft.core.impl.menus.staff;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import gg.modl.minecraft.core.impl.menus.inspect.InspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
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

    // Placeholder Report class since no endpoint exists yet
    public static class Report {
        private final String id;
        private final String type;
        private final String reporterName;
        private final UUID reportedPlayerUuid;
        private final String reportedPlayerName;
        private final String content;
        private final Date date;

        public Report(String id, String type, String reporterName, UUID reportedPlayerUuid,
                      String reportedPlayerName, String content, Date date) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.reportedPlayerUuid = reportedPlayerUuid;
            this.reportedPlayerName = reportedPlayerName;
            this.content = content;
            this.date = date;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getReporterName() { return reporterName; }
        public UUID getReportedPlayerUuid() { return reportedPlayerUuid; }
        public String getReportedPlayerName() { return reportedPlayerName; }
        public String getContent() { return content; }
        public Date getDate() { return date; }
    }

    private List<Report> reports = new ArrayList<>();
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "chat", "cheating", "behavior", "other");
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

        // TODO: Fetch reports when endpoint GET /v1/panel/reports is available
        // For now, list is empty
    }

    /**
     * Set the current filter (used when cycling filters).
     */
    public StaffReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add filter button at slot 40 (y position in navigation row)
        // Note: actionHandler("filter") is already set in MenuItems.filterButton()
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions));

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (reports.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null, null));
        }

        // Filter and sort reports (newest first)
        List<Report> filtered = new ArrayList<>();

        for (Report report : reports) {
            if (currentFilter.equals("all") || report.getType().equalsIgnoreCase(currentFilter)) {
                filtered.add(report);
            }
        }

        // If filtering results in empty list, return placeholder
        if (filtered.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null, null));
        }

        filtered.sort((r1, r2) -> r2.getDate().compareTo(r1.getDate()));
        return filtered;
    }

    @Override
    protected CirrusItem map(Report report) {
        // Handle placeholder for empty list
        if (report.getId() == null) {
            return createEmptyPlaceholder("No reports");
        }

        List<String> lore = new ArrayList<>();

        lore.add(MenuItems.COLOR_GRAY + "Type: " + MenuItems.COLOR_WHITE + report.getType());
        lore.add(MenuItems.COLOR_GRAY + "Reporter: " + MenuItems.COLOR_WHITE + report.getReporterName());
        lore.add(MenuItems.COLOR_GRAY + "Reported: " + MenuItems.COLOR_RED + report.getReportedPlayerName());
        lore.add("");
        lore.addAll(MenuItems.wrapText(report.getContent(), 7));
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Left-click to inspect player");
        lore.add(MenuItems.COLOR_RED + "Right-click to dismiss report");

        ItemType itemType = getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + MenuItems.formatDate(report.getDate())),
                MenuItems.lore(lore)
        );
    }

    private ItemType getReportItemType(String type) {
        if (type == null) return ItemType.PAPER;
        switch (type.toLowerCase()) {
            case "chat":
                return ItemType.WRITABLE_BOOK;
            case "cheating":
                return ItemType.DIAMOND_SWORD;
            case "behavior":
                return ItemType.PLAYER_HEAD;
            default:
                return ItemType.PAPER;
        }
    }

    @Override
    protected void handleClick(Click click, Report report) {
        // Handle placeholder - do nothing
        if (report.getId() == null) {
            return;
        }

        // Check click type for left/right click
        // For now, default to inspect behavior
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
            sendMessage("");
            sendMessage(MenuItems.COLOR_GOLD + "Staff Panel:");
            sendMessage(MenuItems.COLOR_AQUA + panelUrl);
            sendMessage("");
        });

        registerActionHandler("openSettings", ActionHandlers.openMenu(
                new SettingsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));
    }

    private void handleFilter(Click click) {
        // Cycle through filter options
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        String newFilter = filterOptions.get(nextIndex);

        // Refresh menu with new filter - preserve backAction if present
        ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                        .withFilter(newFilter))
                .handle(click);
    }
}
