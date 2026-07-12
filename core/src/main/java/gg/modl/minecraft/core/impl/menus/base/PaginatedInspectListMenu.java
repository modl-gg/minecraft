package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class PaginatedInspectListMenu<T> extends BaseInspectListMenu<T> {

    private final int pageSize;
    protected PaginatedDataSource<T> dataSource;
    private int pageRefreshRequest;

    protected PaginatedInspectListMenu(String title, Platform platform, ModlHttpClient httpClient,
                                       UUID viewerUuid, String viewerName, Account targetAccount,
                                       Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext, int pageSize) {
        super(title, platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        this.pageSize = pageSize;
    }

    @Override
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            int refreshRequest = ++pageRefreshRequest;
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / pageSize + 1, () -> {
                if (refreshRequest != pageRefreshRequest) return;
                openLoadedPage(click, nextPage);
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
    protected Collection<T> elements() {
        List<T> items = dataSource.getAllLoadedItems();
        if (items.isEmpty())
            return Collections.singletonList(emptyElement());
        return items;
    }

    protected abstract T emptyElement();

    protected abstract void openLoadedPage(Click click, int nextPage);
}
