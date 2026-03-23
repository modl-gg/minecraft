package gg.modl.minecraft.core.impl.menus.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class PaginatedDataSource<T> {
    private final List<T> loadedItems = new ArrayList<>();
    private int totalCount;
    private final int pageSize;
    private volatile boolean isFetching;
    private final BiFunction<Integer, Integer, CompletableFuture<FetchResult<T>>> fetcher;
    private Runnable onDataLoaded;

    public PaginatedDataSource(int pageSize, BiFunction<Integer, Integer, CompletableFuture<FetchResult<T>>> fetcher) {
        this.pageSize = pageSize;
        this.fetcher = fetcher;
    }

    public void initialize(List<T> initialItems, int totalCount) {
        loadedItems.clear();
        loadedItems.addAll(initialItems);
        this.totalCount = totalCount;
    }

    public void setOnDataLoaded(Runnable onDataLoaded) {
        this.onDataLoaded = onDataLoaded;
    }

    public List<T> getAllLoadedItems() {
        return new ArrayList<>(loadedItems);
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getTotalMenuPages() {
        return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
    }

    public boolean isPageLoaded(int menuPage) {
        int requiredItems = (menuPage + 1) * pageSize;
        return requiredItems <= loadedItems.size() || loadedItems.size() >= totalCount;
    }

    public void prefetchIfNeeded(int currentMenuPage) {
        int nextPageStart = (currentMenuPage + 1) * pageSize;
        if (nextPageStart < totalCount && nextPageStart >= loadedItems.size() && !isFetching) {
            fetchPage(loadedItems.size() / pageSize + 1);
        }
    }

    public void fetchPage(int apiPage) {
        if (isFetching) return;
        isFetching = true;

        fetcher.apply(apiPage, pageSize).thenAccept(result -> {
            synchronized (loadedItems) {
                int insertOffset = (apiPage - 1) * pageSize;
                if (insertOffset == loadedItems.size()) {
                    loadedItems.addAll(result.items());
                }
                totalCount = result.totalCount();
            }
            isFetching = false;
            if (onDataLoaded != null) {
                onDataLoaded.run();
            }
        }).exceptionally(e -> {
            isFetching = false;
            return null;
        });
    }

    public boolean isFetching() {
        return isFetching;
    }

    @lombok.Value
    public static class FetchResult<T> {
        List<T> items;
        int totalCount;

        public List<T> items() { return this.items; }
        public int totalCount() { return this.totalCount; }
    }
}
