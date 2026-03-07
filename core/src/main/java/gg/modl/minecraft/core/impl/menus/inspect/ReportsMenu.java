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
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;

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

public class ReportsMenu extends BaseInspectListMenu<ReportsMenu.Report> {
    @Getter
    public static class Report {
        private final String id, type, reporterName, content, status;
        private final Date date;

        public Report(String id, String type, String reporterName, String content, String status, Date date) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.content = content;
            this.status = status;
            this.date = date;
        }

    }

    private final List<Report> reports = new ArrayList<>();
    private String currentFilter = "all", currentStatusFilter = "open";
    private final List<String> filterOptions = Arrays.asList("all", "gameplay", "chat");

    public ReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Reports: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        activeTab = InspectTab.REPORTS;

        fetchReports();
    }

    private void fetchReports() {
        try {
            httpClient.getPlayerReports(targetAccount.getMinecraftUuid(), "all").thenAccept(response -> {
                if (response.isSuccess() && response.getReports() != null) {
                    reports.clear();
                    for (ReportsResponse.Report report : response.getReports()) {
                        String type = report.getType() != null ? report.getType() : report.getCategory();
                        if ("player".equalsIgnoreCase(type)) type = "gameplay";
                        reports.add(new Report(
                                report.getId(),
                                type,
                                report.getReporterName(),
                                report.getContent() != null ? report.getContent() : report.getSubject(),
                                report.getStatus(),
                                report.getCreatedAt()
                        ));
                    }
                }
            }).join();
        } catch (Exception ignored) {}
    }

    public ReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    public ReportsMenu withStatusFilter(String statusFilter) {
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
            return Collections.singletonList(new Report(null, null, null, null, null, null));

        List<Report> filtered = new ArrayList<>();
        for (Report report : reports) {
            boolean typeMatch = currentFilter.equals("all") || (report.getType() != null && report.getType().equalsIgnoreCase(currentFilter));
            boolean statusMatch = "open".equalsIgnoreCase(currentStatusFilter) != "closed".equalsIgnoreCase(report.getStatus());
            if (typeMatch && statusMatch)
                filtered.add(report);
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new Report(null, null, null, null, null, null));

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

        if (report.getId() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", report.getType() != null ? report.getType() : "Unknown");
        vars.put("date", report.getDate() != null ? MenuItems.formatDate(report.getDate()) : "Unknown");
        vars.put("reporter", report.getReporterName() != null ? report.getReporterName() : "Unknown");
        vars.put("content", String.join("\n", ReportRenderUtil.processContent(report.getContent())));
        vars.put("reported", targetName);
        vars.put("status", report.getStatus() != null && report.getStatus().equalsIgnoreCase("closed")
                ? "&cCLOSED" : "&aOPEN");

        List<String> lore = ReportRenderUtil.buildLore(locale, "menus.report_item.lore", vars);
        String title = locale.getMessage("menus.report_item.title", vars);
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
    }

    private void dismissReport(Click click, Report report) {
        sendMessage(MenuItems.COLOR_YELLOW + "Dismissing report...");

        httpClient.dismissReport(report.getId(), viewerName, "Insufficient evidence").thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Report dismissed.");

            ReportsMenu refreshed = new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .withFilter(currentFilter).withStatusFilter(currentStatusFilter);
            ActionHandlers.openMenu(refreshed).handle(click);
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to dismiss report: " + e.getMessage());
            return null;
        });
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("filter", this::handleFilter);
        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        registerActionHandler("openReports", click -> {});
    }

    private void handleFilter(Click click) {
        if (click.clickType().equals(CirrusClickType.RIGHT_CLICK)) {
            String newStatus = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
            ActionHandlers.openMenu(
                    new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                            .withFilter(currentFilter)
                            .withStatusFilter(newStatus))
                    .handle(click);
        } else {
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
