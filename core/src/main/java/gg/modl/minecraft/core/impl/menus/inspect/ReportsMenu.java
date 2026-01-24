package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reports Menu - displays reports filed against a player.
 */
public class ReportsMenu extends BaseInspectListMenu<ReportsMenu.Report> {

    // Placeholder Report class since no endpoint exists yet
    public static class Report {
        private final String id;
        private final String type;
        private final String reporterName;
        private final String content;
        private final Date date;
        private final String status;

        public Report(String id, String type, String reporterName, String content, Date date, String status) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.content = content;
            this.date = date;
            this.status = status;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getReporterName() { return reporterName; }
        public String getContent() { return content; }
        public Date getDate() { return date; }
        public String getStatus() { return status; }
    }

    private List<Report> reports = new ArrayList<>();
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "chat", "cheating", "behavior", "other");
    private final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new reports menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public ReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Reports: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.REPORTS;

        // TODO: Fetch reports when endpoint GET /v1/panel/players/{uuid}/reports is available
        // For now, reports list is empty
    }

    private static String getPlayerNameStatic(Account account) {
        if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add filter button
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions)
                .actionHandler("filter"));

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        // Filter reports based on current filter
        if (currentFilter.equals("all")) {
            return reports;
        }

        List<Report> filtered = new ArrayList<>();
        for (Report report : reports) {
            if (report.getType().equalsIgnoreCase(currentFilter)) {
                filtered.add(report);
            }
        }
        return filtered;
    }

    @Override
    protected CirrusItem map(Report report) {
        List<String> lore = new ArrayList<>();

        lore.add(MenuItems.COLOR_GRAY + "Type: " + MenuItems.COLOR_WHITE + report.getType());
        lore.add(MenuItems.COLOR_GRAY + "Reporter: " + MenuItems.COLOR_WHITE + report.getReporterName());
        lore.add(MenuItems.COLOR_GRAY + "Status: " + getStatusColor(report.getStatus()) + report.getStatus());
        lore.add("");
        lore.addAll(MenuItems.wrapText(report.getContent(), 7));

        ItemType itemType = getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + MenuItems.formatDate(report.getDate())),
                MenuItems.lore(lore)
        );
    }

    private String getStatusColor(String status) {
        if (status == null) return MenuItems.COLOR_GRAY;
        switch (status.toLowerCase()) {
            case "open":
                return MenuItems.COLOR_RED;
            case "in_progress":
                return MenuItems.COLOR_YELLOW;
            case "closed":
            case "resolved":
                return MenuItems.COLOR_GREEN;
            default:
                return MenuItems.COLOR_GRAY;
        }
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
        // Reports are view-only in this menu
        // Could open a report detail view in the future
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Filter handler
        registerActionHandler("filter", this::handleFilter);

        // Override header navigation handlers
        registerActionHandler("openNotes", click -> {
            click.clickedMenu().close();
            new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openAlts", click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openHistory", click -> {
            click.clickedMenu().close();
            new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openReports", click -> {
            // Already on reports, do nothing
        });
        registerActionHandler("openPunish", click -> {
            click.clickedMenu().close();
            new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
    }

    private void handleFilter(Click click) {
        // Cycle through filter options
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        currentFilter = filterOptions.get(nextIndex);

        // Refresh menu
        click.clickedMenu().close();
        new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                .display(click.player());
    }
}
