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
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
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
    private final List<String> filterOptions = Arrays.asList("all", "gameplay", "chat", "cheating", "behavior", "other");
    private final String panelUrl;

    public StaffReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                            boolean isAdmin, String panelUrl, Consumer<CirrusPlayerWrapper> backAction) {
        super("Reports", platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.panelUrl = panelUrl;
        activeTab = StaffTab.REPORTS;

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

                        String type = report.getType() != null ? report.getType() : report.getCategory();
                        if ("player".equalsIgnoreCase(type)) type = "gameplay";
                        reports.add(new Report(
                                report.getId(),
                                type,
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
        } catch (Exception ignored) {}
    }

    public StaffReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    public StaffReportsMenu withStatusFilter(String statusFilter) {
        this.currentStatusFilter = statusFilter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.FILTER_BUTTON, MenuItems.filterButton(currentFilter, filterOptions, currentStatusFilter, "reports"));

        return items;
    }

    @Override
    protected Collection<Report> elements() {
        if (reports.isEmpty())
            return Collections.singletonList(new Report(null, null, null, null, null, null, null, null));

        List<Report> filtered = new ArrayList<>();

        for (Report report : reports) {
            boolean typeMatch = currentFilter.equals("all") || (report.getType() != null && report.getType().equalsIgnoreCase(currentFilter));
            boolean statusMatch = "open".equalsIgnoreCase(currentStatusFilter)
                    ? !"closed".equalsIgnoreCase(report.getStatus())
                    : "closed".equalsIgnoreCase(report.getStatus());
            if (typeMatch && statusMatch)
                filtered.add(report);
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new Report(null, null, null, null, null, null, null, null));

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

        if (report.getId() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.staff_reports"));

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", report.getType() != null ? report.getType() : "Unknown");
        vars.put("reporter", report.getReporterName() != null ? report.getReporterName() : "Unknown");
        vars.put("reported", report.getReportedPlayerName() != null ? report.getReportedPlayerName() : "Unknown");
        vars.put("date", MenuItems.formatDate(report.getDate()));
        vars.put("status", report.getStatus() != null && report.getStatus().equalsIgnoreCase("closed")
                ? "&cCLOSED" : "&aOPEN");
        vars.put("content", String.join("\n", ReportRenderUtil.processContent(report.getContent())));

        List<String> lore = ReportRenderUtil.buildLore(locale, "menus.staff_report_item.lore", vars);
        String title = locale.getMessage("menus.staff_report_item.title", vars);
        CirrusItemType itemType = ReportRenderUtil.getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Report report) {
        if (report.getId() == null) return;

        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK))
            dismissReport(click, report);
        else
            inspectPlayer(click, report);
    }

    private void dismissReport(Click click, Report report) {
        sendMessage(MenuItems.COLOR_YELLOW + "Dismissing report...");

        httpClient.dismissReport(report.getId(), viewerName, "Insufficient evidence").thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Report dismissed.");

            StaffReportsMenu refreshed = new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                    .withFilter(currentFilter).withStatusFilter(currentStatusFilter);
            ActionHandlers.openMenu(refreshed).handle(click);
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

        httpClient.getPlayerProfile(report.getReportedPlayerUuid()).thenAccept(response -> {
            if (response.getStatus() == 200) {
                new InspectMenu(platform, httpClient, viewerUuid, viewerName, response.getProfile(),
                    p -> new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, null).display(p))
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

        registerActionHandler("filter", this::handleFilter);

        StaffNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("openReports", click -> {});
    }

    private void handleFilter(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
            ActionHandlers.openMenu(
                    new StaffReportsMenu(platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl, backAction)
                            .withFilter(currentFilter)
                            .withStatusFilter(newStatus))
                    .handle(click);
        } else {
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
