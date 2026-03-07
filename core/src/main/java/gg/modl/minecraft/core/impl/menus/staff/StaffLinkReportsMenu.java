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
import gg.modl.minecraft.api.http.response.ReportsResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseStaffListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.StaffNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.StaffTabItems.StaffTab;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StaffLinkReportsMenu extends BaseStaffListMenu<StaffLinkReportsMenu.Report> {
    @Getter
    public static class Report {
        private final String id, type, reporterName, content;
        private final Date date;

        public Report(String id, String type, String reporterName, String content, Date date) {
            this.id = id;
            this.type = type;
            this.reporterName = reporterName;
            this.content = content;
            this.date = date;
        }

    }

    private final Account targetAccount;
    private final UUID targetUuid;
    private final List<Report> reports = new ArrayList<>();
    private final Set<String> selectedReportIds;
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "gameplay", "chat");
    private final Consumer<Set<String>> onComplete;
    private final String panelUrl;

    public StaffLinkReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 boolean isAdmin, String panelUrl, Account targetAccount, Set<String> preSelectedIds,
                                 Consumer<CirrusPlayerWrapper> backAction, Consumer<Set<String>> onComplete) {
        super("Link Reports: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, isAdmin, backAction);
        this.targetAccount = targetAccount;
        this.targetUuid = targetAccount.getMinecraftUuid();
        this.selectedReportIds = new LinkedHashSet<>(preSelectedIds);
        this.onComplete = onComplete;
        this.panelUrl = panelUrl;

        activeTab = StaffTab.PUNISHMENTS;

        fetchReports();
    }

    private void fetchReports() {
        try {
            httpClient.getPlayerReports(targetUuid, "Open").thenAccept(response -> {
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
                                report.getCreatedAt()
                        ));
                    }
                }
            }).join();
        } catch (Exception ignored) {
        }
    }

    public StaffLinkReportsMenu withFilter(String filter) {
        this.currentFilter = filter;
        return this;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(39, MenuItems.filterButton(currentFilter, filterOptions));

        items.put(40, CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Apply Selection"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Save selection and return",
                        "",
                        MenuItems.COLOR_YELLOW + selectedReportIds.size() + " report(s) selected"
                )
        ).actionHandler("applySelection"));

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
        if (reports.isEmpty())
            return Collections.singletonList(new Report(null, null, null, null, null));

        List<Report> filtered;
        if (currentFilter.equals("all")) {
            filtered = reports;
        } else {
            filtered = reports.stream()
                    .filter(r -> r.getType() != null && r.getType().equalsIgnoreCase(currentFilter))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty())
            return Collections.singletonList(new Report(null, null, null, null, null));

        return filtered;
    }

    @Override
    protected CirrusItem map(Report report) {
        LocaleManager locale = platform.getLocaleManager();

        if (report.getId() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));

        boolean selected = selectedReportIds.contains(report.getId());

        Map<String, String> vars = new HashMap<>();
        vars.put("id", report.getId());
        vars.put("type", report.getType() != null ? report.getType() : "Unknown");
        vars.put("date", report.getDate() != null ? MenuItems.formatDate(report.getDate()) : "Unknown");
        vars.put("reporter", report.getReporterName() != null ? report.getReporterName() : "Unknown");
        vars.put("content", String.join("\n", ReportRenderUtil.processContent(report.getContent())));

        String localeKey = selected ? "menus.link_report_item_selected" : "menus.link_report_item_unselected";
        List<String> lore = ReportRenderUtil.buildLore(locale, localeKey + ".lore", vars);
        String title = locale.getMessage(localeKey + ".title", vars);
        CirrusItemType itemType = selected ? CirrusItemType.LIME_DYE : ReportRenderUtil.getReportItemType(report.getType());

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        ).actionHandler("toggleReport_" + report.getId());
    }

    @Override
    protected void handleClick(Click click, Report report) {
        if (report.getId() == null) return;

        if (selectedReportIds.contains(report.getId()))
            selectedReportIds.remove(report.getId());
        else
            selectedReportIds.add(report.getId());

        StaffLinkReportsMenu refreshed = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                isAdmin, panelUrl, targetAccount, selectedReportIds, backAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        StaffNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, isAdmin, panelUrl);

        registerActionHandler("filter", this::handleFilter);

        registerActionHandler("applySelection", click -> {
            handleApply(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("selectAll", click -> {
            handleSelectAll(click);
            return CallResult.DENY_GRABBING;
        });

        for (Report report : reports) {
            if (report.getId() != null) {
                registerActionHandler("toggleReport_" + report.getId(), click -> {
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
            for (Report report : filtered)
                if (report.getId() != null)
                    selectedReportIds.remove(report.getId());
        } else {
            for (Report report : filtered)
                if (report.getId() != null)
                    selectedReportIds.add(report.getId());
        }

        StaffLinkReportsMenu refreshed = new StaffLinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                isAdmin, panelUrl, targetAccount, selectedReportIds, backAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }
}
