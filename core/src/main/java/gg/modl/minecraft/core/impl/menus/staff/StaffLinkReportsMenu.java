package gg.modl.minecraft.core.impl.menus.staff;

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
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Staff variant of the Link Reports Menu.
 * Uses the staff menu header instead of the inspect menu header.
 */
public class StaffLinkReportsMenu extends BaseStaffListMenu<StaffLinkReportsMenu.Report> {

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

    private final Account targetAccount;
    private final UUID targetUuid;
    private List<Report> reports = new ArrayList<>();
    private final Set<String> selectedReportIds;
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "player", "chat");
    private final Consumer<Set<String>> onComplete;
    private final String panelUrl;

    public StaffLinkReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 boolean isAdmin, String panelUrl, Account targetAccount, Set<String> preSelectedIds,
                                 Consumer<CirrusPlayerWrapper> backAction, Consumer<Set<String>> onComplete) {
        super("Link Reports: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.targetAccount = targetAccount;
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.selectedReportIds = new LinkedHashSet<>(preSelectedIds);
        this.onComplete = onComplete;
        this.panelUrl = panelUrl;

        activeTab = StaffTab.PUNISHMENTS;

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

    public StaffLinkReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Slot 39: Filter button
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

        if (report.getId() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));
        }

        boolean selected = selectedReportIds.contains(report.getId());

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

        String localeKey = selected ? "menus.link_report_item_selected" : "menus.link_report_item_unselected";

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList(localeKey + ".lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n")) {
                for (String subLine : processed.split("\n")) {
                    lore.add(subLine);
                }
            } else if (!processed.isEmpty()) {
                lore.add(processed);
            }
        }

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

        if (selectedReportIds.contains(report.getId())) {
            selectedReportIds.remove(report.getId());
        } else {
            selectedReportIds.add(report.getId());
        }

        StaffLinkReportsMenu refreshed = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                isAdmin, panelUrl, targetAccount, selectedReportIds, backAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Staff header navigation handlers
        registerActionHandler("openOnlinePlayers", ActionHandlers.openMenu(
                new OnlinePlayersMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

        registerActionHandler("openReports", ActionHandlers.openMenu(
                new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null)));

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

        StaffLinkReportsMenu refreshed = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                isAdmin, panelUrl, targetAccount, selectedReportIds, backAction, onComplete);
        refreshed.withFilter(newFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    private void handleApply(Click click) {
        click.clickedMenu().close();
        onComplete.accept(selectedReportIds);
    }

    private void handleSelectAll(Click click) {
        List<Report> filtered;
        if (currentFilter.equals("all")) {
            filtered = reports;
        } else {
            filtered = reports.stream()
                    .filter(r -> r.getType() != null && r.getType().equalsIgnoreCase(currentFilter))
                    .collect(Collectors.toList());
        }

        boolean allSelected = filtered.stream()
                .filter(r -> r.getId() != null)
                .allMatch(r -> selectedReportIds.contains(r.getId()));

        if (allSelected) {
            for (Report report : filtered) {
                if (report.getId() != null) {
                    selectedReportIds.remove(report.getId());
                }
            }
        } else {
            for (Report report : filtered) {
                if (report.getId() != null) {
                    selectedReportIds.add(report.getId());
                }
            }
        }

        StaffLinkReportsMenu refreshed = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                isAdmin, panelUrl, targetAccount, selectedReportIds, backAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }
}
