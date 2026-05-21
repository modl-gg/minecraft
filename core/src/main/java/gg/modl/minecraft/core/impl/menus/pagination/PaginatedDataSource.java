package gg.modl.minecraft.core.impl.menus.pagination;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class PaginatedDataSource<T> {
    private final List<T> loadedItems = new ArrayList<>();
    private int totalCount;
    private final int pageSize;
    private final AtomicBoolean isFetching = new AtomicBoolean();
    private final BiFunction<Integer, Integer, CompletableFuture<FetchResult<T>>> fetcher;
    private Runnable onDataLoaded;

    public PaginatedDataSource(int pageSize, BiFunction<Integer, Integer, CompletableFuture<FetchResult<T>>> fetcher) {
        this.pageSize = pageSize;
        this.fetcher = fetcher;
    }

    public void initialize(List<T> initialItems, int totalCount) {
        synchronized (loadedItems) {
            loadedItems.clear();
            loadedItems.addAll(initialItems);
            this.totalCount = totalCount;
        }
    }

    public void setOnDataLoaded(Runnable onDataLoaded) {
        synchronized (this) {
            this.onDataLoaded = onDataLoaded;
        }
    }

    public List<T> getAllLoadedItems() {
        synchronized (loadedItems) {
            return new ArrayList<>(loadedItems);
        }
    }

    public int getTotalCount() {
        synchronized (loadedItems) {
            return totalCount;
        }
    }

    public int getTotalMenuPages() {
        synchronized (loadedItems) {
            return Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        }
    }

    public boolean isPageLoaded(int menuPage) {
        synchronized (loadedItems) {
            int requiredItems = (menuPage + 1) * pageSize;
            return requiredItems <= loadedItems.size() || loadedItems.size() >= totalCount;
        }
    }

    public void prefetchIfNeeded(int currentMenuPage) {
        int apiPage;
        synchronized (loadedItems) {
            int nextPageStart = (currentMenuPage + 1) * pageSize;
            if (nextPageStart >= totalCount || nextPageStart < loadedItems.size()) {
                return;
            }
            apiPage = loadedItems.size() / pageSize + 1;
        }
        fetchPage(apiPage);
    }

    public boolean fetchPage(int apiPage) {
        return fetchPage(apiPage, null);
    }

    public boolean fetchPage(int apiPage, Runnable onDataLoaded) {
        if (onDataLoaded != null) {
            setOnDataLoaded(onDataLoaded);
        }
        if (!isFetching.compareAndSet(false, true)) return false;

        CompletableFuture<FetchResult<T>> fetchFuture;
        try {
            fetchFuture = fetcher.apply(apiPage, pageSize);
        } catch (Exception e) {
            clearFetchState();
            return true;
        }
        if (fetchFuture == null) {
            clearFetchState();
            return true;
        }

        fetchFuture.whenComplete((result, throwable) -> {
            if (throwable == null && result != null) {
                synchronized (loadedItems) {
                    int insertOffset = (apiPage - 1) * pageSize;
                    if (insertOffset == loadedItems.size()) {
                        loadedItems.addAll(result.items());
                    }
                    totalCount = result.totalCount();
                }
            }

            Runnable callback = clearFetchState();
            if (throwable == null && callback != null) {
                callback.run();
            }
        });
        return true;
    }

    private Runnable clearFetchState() {
        Runnable callback;
        synchronized (this) {
            callback = onDataLoaded;
            onDataLoaded = null;
        }
        isFetching.set(false);
        return callback;
    }

    public boolean isFetching() {
        return isFetching.get();
    }

    @Value
    public static class FetchResult<T> {
        List<T> items;
        int totalCount;

        public List<T> items() { return this.items; }
        public int totalCount() { return this.totalCount; }
    }
}
