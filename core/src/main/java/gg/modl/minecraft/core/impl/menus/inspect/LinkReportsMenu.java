package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Link Reports Menu - allows staff to select reports to link with a punishment.
 * Reports can be toggled on/off, filtered by type, and bulk-selected.
 */
public class LinkReportsMenu extends BaseInspectListMenu<LinkReportsMenu.Report> {

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
    private final Set<String> selectedReportIds;
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "player", "chat", "cheating", "behavior", "other");
    private final Consumer<Set<String>> onComplete; // callback with selected report IDs
    private final Consumer<CirrusPlayerWrapper> rootBackAction;

    /**
     * Create a new link reports menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account of the player being punished
     * @param preSelectedIds Already-selected report IDs from PunishSeverityMenu
     * @param backAction Action to return to parent menu
     * @param rootBackAction Root back action for primary tab navigation
     * @param onComplete Callback invoked with selected report IDs when applying
     */
    public LinkReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Set<String> preSelectedIds,
                           Consumer<CirrusPlayerWrapper> backAction, Consumer<CirrusPlayerWrapper> rootBackAction,
                           Consumer<Set<String>> onComplete) {
        super("Link Reports: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.selectedReportIds = new LinkedHashSet<>(preSelectedIds);
        this.onComplete = onComplete;
        this.rootBackAction = rootBackAction;

        fetchReports();
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

    private void fetchReports() {
        try {
            httpClient.getPlayerReports(targetUuid, "Open").thenAccept(response -> {
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
            // Failed to fetch - list remains empty
        }
    }

    /**
     * Set the current filter.
     */
    public LinkReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Slot 38: Previous page (already set by super)
        // Slot 39: Filter button (moved 1 left from standard 40)
        items.put(39, MenuItems.filterButton(currentFilter, filterOptions));

        // Slot 40: Apply selection (sign)
        items.put(40, CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Apply Selection"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Save selection and return",
                        "",
                        MenuItems.COLOR_YELLOW + selectedReportIds.size() + " report(s) selected"
                )
        ).actionHandler("applySelection"));

        // Slot 41: Select All (emerald)
        items.put(41, CirrusItem.of(
                CirrusItemType.EMERALD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Select All"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle all currently filtered reports"
                )
        ).actionHandler("selectAll"));

        // Slot 42: Next page (already set by super)

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        if (reports.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null));
        }

        List<Report> filtered;
        if (currentFilter.equals("all")) {
            filtered = reports;
        } else {
            filtered = reports.stream()
                    .filter(r -> r.getType() != null && r.getType().equalsIgnoreCase(currentFilter))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            return Collections.singletonList(new Report(null, null, null, null, null, null));
        }

        return filtered;
    }

    @Override
    protected CirrusItem map(Report report) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (report.getId() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));
        }

        boolean selected = selectedReportIds.contains(report.getId());

        String reportType = report.getType() != null ? report.getType() : "Unknown";
        String reporter = report.getReporterName() != null ? report.getReporterName() : "Unknown";
        String content = report.getContent() != null ? report.getContent() : "";
        String formattedDate = report.getDate() != null ? MenuItems.formatDate(report.getDate()) : "Unknown";

        // Normalize newlines, strip markdown, and wrap content
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

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", reportType);
        vars.put("date", formattedDate);
        vars.put("reporter", reporter);
        vars.put("content", String.join("\n", wrappedContent));

        // Use selected or unselected locale key
        String localeKey = selected ? "menus.link_report_item_selected" : "menus.link_report_item_unselected";

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList(localeKey + ".lore")) {
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

        // Get title from locale
        String title = locale.getMessage(localeKey + ".title", vars);

        CirrusItemType itemType = selected ? CirrusItemType.LIME_DYE : getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        ).actionHandler("toggleReport_" + report.getId());
    }

    private CirrusItemType getReportItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        return switch (type.toLowerCase()) {
            case "player" -> CirrusItemType.PLAYER_HEAD;
            case "chat" -> CirrusItemType.WRITABLE_BOOK;
            case "cheating" -> CirrusItemType.DIAMOND_SWORD;
            case "behavior" -> CirrusItemType.SKELETON_SKULL;
            default -> CirrusItemType.PAPER;
        };
    }

    @Override
    protected void handleClick(Click click, Report report) {
        if (report.getId() == null) {
            return;
        }

        // Toggle selection
        if (selectedReportIds.contains(report.getId())) {
            selectedReportIds.remove(report.getId());
        } else {
            selectedReportIds.add(report.getId());
        }

        // Reopen menu with updated selection state
        LinkReportsMenu refreshed = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, selectedReportIds, backAction, rootBackAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Inspect tab navigation handlers
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction)));

        // Filter handler
        registerActionHandler("filter", this::handleFilter);

        // Apply selection handler
        registerActionHandler("applySelection", (ActionHandler) click -> {
            handleApply(click);
            return CallResult.DENY_GRABBING;
        });

        // Select All handler
        registerActionHandler("selectAll", (ActionHandler) click -> {
            handleSelectAll(click);
            return CallResult.DENY_GRABBING;
        });

        // Register toggle handlers for each report
        for (Report report : reports) {
            if (report.getId() != null) {
                registerActionHandler("toggleReport_" + report.getId(), (ActionHandler) click -> {
                    handleClick(click, report);
                    return CallResult.DENY_GRABBING;
                });
            }
        }
    }

    private void handleFilter(Click click) {
        int currentIndex = filterOptions.indexOf(currentFilter);
        int nextIndex = (currentIndex + 1) % filterOptions.size();
        String newFilter = filterOptions.get(nextIndex);

        LinkReportsMenu refreshed = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, selectedReportIds, backAction, rootBackAction, onComplete);
        refreshed.withFilter(newFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    private void handleApply(Click click) {
        click.clickedMenu().close();
        onComplete.accept(selectedReportIds);
    }

    private void handleSelectAll(Click click) {
        // Get currently filtered reports
        List<Report> filtered;
        if (currentFilter.equals("all")) {
            filtered = reports;
        } else {
            filtered = reports.stream()
                    .filter(r -> r.getType() != null && r.getType().equalsIgnoreCase(currentFilter))
                    .collect(Collectors.toList());
        }

        // Check if all filtered reports are already selected
        boolean allSelected = filtered.stream()
                .filter(r -> r.getId() != null)
                .allMatch(r -> selectedReportIds.contains(r.getId()));

        if (allSelected) {
            // Deselect all filtered reports
            for (Report report : filtered) {
                if (report.getId() != null) {
                    selectedReportIds.remove(report.getId());
                }
            }
        } else {
            // Select all filtered reports
            for (Report report : filtered) {
                if (report.getId() != null) {
                    selectedReportIds.add(report.getId());
                }
            }
        }

        // Reopen menu
        LinkReportsMenu refreshed = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, selectedReportIds, backAction, rootBackAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }
}
