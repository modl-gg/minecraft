package gg.modl.minecraft.core.impl.menus.inspect;

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
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil.LinkableReport;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class LinkReportsMenu extends BaseInspectListMenu<LinkableReport> {
    private final List<LinkableReport> reports;
    private final Set<String> selectedReportIds;
    private String currentFilter = "all";
    private final List<String> filterOptions = Arrays.asList("all", "gameplay", "chat");
    private final Consumer<Set<String>> onComplete; // callback with selected report IDs
    private final Consumer<CirrusPlayerWrapper> rootBackAction;

    public LinkReportsMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Set<String> preSelectedIds,
                           Consumer<CirrusPlayerWrapper> backAction, Consumer<CirrusPlayerWrapper> rootBackAction,
                           Consumer<Set<String>> onComplete) {
        super("Link Reports: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.selectedReportIds = new LinkedHashSet<>(preSelectedIds);
        this.onComplete = onComplete;
        this.rootBackAction = rootBackAction;

        reports = ReportRenderUtil.loadLinkableReports(httpClient, targetUuid);
    }

    public LinkReportsMenu withFilter(String filter) {
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
    protected Collection<LinkableReport> elements() {
        return ReportRenderUtil.elementsOrEmptyReports(reports, currentFilter);
    }

    @Override
    protected CirrusItem map(LinkableReport report) {
        LocaleManager locale = platform.getLocaleManager();

        if (report.getId() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.reports"));

        return ReportRenderUtil.mapLinkableReport(report, selectedReportIds, locale);
    }

    @Override
    protected void handleClick(Click click, LinkableReport report) {
        if (report.getId() == null) return;

        ReportRenderUtil.toggleReportSelection(selectedReportIds, report.getId());

        LinkReportsMenu refreshed = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, selectedReportIds, backAction, rootBackAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, rootBackAction);

        registerActionHandler("filter", this::handleFilter);

        registerActionHandler("applySelection", click -> {
            handleApply(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("selectAll", click -> {
            handleSelectAll(click);
            return CallResult.DENY_GRABBING;
        });

        for (LinkableReport report : reports) {
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
        List<LinkableReport> filtered = ReportRenderUtil.filterLinkableReports(reports, currentFilter);
        ReportRenderUtil.toggleFilteredReportSelection(selectedReportIds, filtered);

        LinkReportsMenu refreshed = new LinkReportsMenu(platform, httpClient, viewerUuid, viewerName,
                targetAccount, selectedReportIds, backAction, rootBackAction, onComplete);
        refreshed.withFilter(currentFilter);
        ActionHandlers.openMenu(refreshed).handle(click);
    }
}
