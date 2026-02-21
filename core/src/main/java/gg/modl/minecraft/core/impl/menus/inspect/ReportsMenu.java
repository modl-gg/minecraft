package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CirrusClickType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
    private String currentStatusFilter = "open";
    private final List<String> filterOptions = Arrays.asList("all", "player", "chat", "cheating", "behavior", "other");
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

        // Fetch reports filed against this player
        fetchReports();
    }

    private void fetchReports() {
        try {
            httpClient.getPlayerReports(targetAccount.getMinecraftUuid(), "all").thenAccept(response -> {
                if (response.isSuccess() && response.getReports() != null) {
                    reports.clear();
                    for (var report : response.getReports()) {
                        reports.add(new Report(
                                report.getId(),
                                report.getType() != null ? report.getType() : report.getCategory(),
                                report.getReporterName(),
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
    public ReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    /**
     * Set the current status filter (open/closed).
     */
    public ReportsMenu withStatusFilter(String statusFilter) {
        this.currentStatusFilter = statusFilter;
        return this;
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

        // Add filter button at slot 40 (y position in navigation row)
        // Note: actionHandler("filter") is already set in MenuItems.filterButton()
        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions, currentStatusFilter, "reports"));

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (reports.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null));
        }

        // Filter reports based on current type and status filter
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
            return Collections.singletonList(new Report(null, null, null, null, null, null));
        }

        // Sort newest first with null-safe dates
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
            return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));
        }

        // Build variables map
        String reportType = report.getType() != null ? report.getType() : "Unknown";
        String reporter = report.getReporterName() != null ? report.getReporterName() : "Unknown";
        String content = report.getContent() != null ? report.getContent() : "";
        String formattedDate = report.getDate() != null ? MenuItems.formatDate(report.getDate()) : "Unknown";

        content = StringUtil.unescapeNewlines(content);
        content = content.replace("**", "").replace("```", "");
        List<String> wrappedContent = new ArrayList<>();
        for (String paragraph : content.split("\n")) {
            if (paragraph.trim().isEmpty()) {
                wrappedContent.add("");
            } else {
                wrappedContent.addAll(MenuItems.wrapText(paragraph.trim(), 7));
            }
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", reportType);
        vars.put("date", formattedDate);
        vars.put("reporter", reporter);
        vars.put("content", String.join("\n", wrappedContent));
        vars.put("reported", targetName);
        vars.put("status", report.getStatus() != null && report.getStatus().equalsIgnoreCase("closed")
                ? "&cCLOSED" : "&aOPEN");

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.report_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            // Handle content with newlines - split into separate lore lines
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

        // Add click instructions for open reports
        if (report.getStatus() == null || !"closed".equalsIgnoreCase(report.getStatus())) {
            lore.add("");
            lore.add(MenuItems.COLOR_YELLOW + "Right-click to dismiss report");
        }

        // Get title from locale
        String title = locale.getMessage("menus.report_item.title", vars);

        CirrusItemType itemType = getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    private String getStatusColor(String status) {
        if (status == null) return MenuItems.COLOR_GRAY;
        switch (status.toLowerCase()) {
            case "open":
                return MenuItems.COLOR_RED;
            case "unfinished":
                return MenuItems.COLOR_YELLOW;
            case "closed":
                return MenuItems.COLOR_GREEN;
            default:
                return MenuItems.COLOR_GRAY;
        }
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
            dismissReport(click, report);
        }
    }

    private void dismissReport(Click click, Report report) {
        sendMessage(MenuItems.COLOR_YELLOW + "Dismissing report...");

        httpClient.dismissReport(report.getId(), viewerName, "Insufficient evidence").thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Report dismissed.");

            // Refresh menu
            ReportsMenu refreshed = new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .withFilter(currentFilter).withStatusFilter(currentStatusFilter);
            platform.runOnMainThread(() -> ActionHandlers.openMenu(refreshed).handle(click));
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to dismiss report: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Filter handler
        registerActionHandler("filter", this::handleFilter);

        // Override header navigation handlers - pass backAction to preserve the back button
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openReports", click -> {
            // Already on reports, do nothing
        });
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
    }

    private void handleFilter(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            // Toggle status filter between open and closed
            String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
            ActionHandlers.openMenu(
                    new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                            .withFilter(currentFilter)
                            .withStatusFilter(newStatus))
                    .handle(click);
        } else {
            // Cycle through type filter options
            int currentIndex = filterOptions.indexOf(currentFilter);
            int nextIndex = (currentIndex + 1) % filterOptions.size();
            String newFilter = filterOptions.get(nextIndex);

            ActionHandlers.openMenu(
                    new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                            .withFilter(newFilter)
                            .withStatusFilter(currentStatusFilter))
                    .handle(click);
        }
    }
}
