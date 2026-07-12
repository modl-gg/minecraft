package gg.modl.minecraft.core.impl.menus.inspect;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource.FetchResult;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuAsync;
import gg.modl.minecraft.core.impl.menus.util.PunishmentItemRenderer;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HistoryMenu extends BaseInspectListMenu<Punishment> {
    private static final int PAGE_SIZE = 7;

    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();
    private final PaginatedDataSource<Punishment> dataSource;
    private int pageRefreshRequest;
    @Getter private final CompletableFuture<Void> dataFuture;

    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("History: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.HISTORY;

        this.dataFuture = loadPunishmentTypes();

        int totalCount = inspectContext != null ? inspectContext.punishmentCount() : targetAccount.getPunishments().size();
        dataSource = new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<FetchResult<Punishment>> future = new CompletableFuture<>();
            httpClient.getPlayerPunishments(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    future.complete(new FetchResult<>(response.getPunishments(), response.getTotalCount()));
                } else {
                    future.complete(new FetchResult<>(listOf(), 0, false));
                }
            }).exceptionally(e -> {
                future.complete(new FetchResult<>(listOf(), totalCount, false));
                return null;
            });
            return future;
        });

        List<Punishment> initial = new ArrayList<>(targetAccount.getPunishments());
        initial.sort(Comparator.comparing(Punishment::getIssued, Comparator.nullsLast(Comparator.reverseOrder())));
        dataSource.initialize(initial, totalCount);
    }

    private CompletableFuture<Void> loadPunishmentTypes() {
        return httpClient.getPunishmentTypes().thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                for (PunishmentTypesResponse.PunishmentTypeData type : response.getData()) {
                    typesByOrdinal.put(type.getOrdinal(), type);
                }
            }
        }).exceptionally(e -> null);
    }

    @Override
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            int refreshRequest = ++pageRefreshRequest;
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / PAGE_SIZE + 1, () -> {
                if (refreshRequest != pageRefreshRequest) return;
                HistoryMenu newMenu = new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
                newMenu.dataSource.initialize(dataSource.getAllLoadedItems(), dataSource.getTotalCount());
                newMenu.setInitialPage(nextPage);
                MenuAsync.displayWhenLoaded(platform, newMenu.getDataFuture(), click.player(), newMenu::display);
            });
            return true;
        }
        dataSource.prefetchIfNeeded(nextPage);
        return false;
    }

    @Override
    public boolean hasNextPage() {
        return currentPageIndex().get() < dataSource.getTotalMenuPages() - 1;
    }

    @Override
    protected Collection<Punishment> elements() {
        List<Punishment> punishments = dataSource.getAllLoadedItems();
        if (punishments.isEmpty())
            return Collections.singletonList(new Punishment());
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        LocaleManager locale = PluginServices.locale();

        if (punishment.getId() == null || punishment.getId().isEmpty())
            return createEmptyPlaceholder(locale.getMessage("menus.empty.history"));

        String typeName = getTypeName(punishment);
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        boolean isKick = typeData != null && typeData.isKick();
        boolean isBan = typeData != null && typeData.isBan();
        boolean isMute = typeData != null && typeData.isMute();

        return PunishmentItemRenderer.render(punishment, isKick, isBan, isMute, typeName,
                "menus.history_item", Collections.emptyMap());
    }

    private String getTypeName(Punishment punishment) {
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getName() != null) {
            return typeData.getName();
        }

        Object typeName = punishment.getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }

        return punishment.getTypeCategory();
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
        if (punishment.getId() == null || punishment.getId().isEmpty())
            return;

        ActionHandlers.openMenu(
                new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishment, backAction,
                        p -> {
                            HistoryMenu m = new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
                            MenuAsync.displayWhenLoaded(platform, m.getDataFuture(), p, m::display);
                        }))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        InspectNavigationHandlers.registerAll(
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        registerActionHandler("openHistory", click -> {});
    }
}
