package gg.modl.minecraft.core.impl.menus.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Logger;

public class PaginatedDataSource<T> {
    private static final Logger logger = Logger.getLogger(PaginatedDataSource.class.getName());

    private final List<T> loadedItems = new ArrayList<>();
    private int totalCount;
    private int generation;
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
            this.generation++;
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

        final int fetchGeneration;
        synchronized (loadedItems) {
            fetchGeneration = generation;
        }

        CompletableFuture<FetchResult<T>> fetchFuture;
        try {
            fetchFuture = fetcher.apply(apiPage, pageSize);
        } catch (Exception e) {
            clearFetchStateAndNotify();
            return true;
        }
        if (fetchFuture == null) {
            clearFetchStateAndNotify();
            return true;
        }

        fetchFuture.whenComplete((result, throwable) -> {
            if (throwable == null && result != null && result.success()) {
                synchronized (loadedItems) {
                    if (fetchGeneration == generation) {
                        int insertOffset = (apiPage - 1) * pageSize;
                        if (insertOffset == loadedItems.size()) {
                            loadedItems.addAll(result.items());
                        }
                        totalCount = result.totalCount();
                    } else {
                        logger.warning("Dropping stale page fetch " + apiPage + " from generation "
                                + fetchGeneration + " (current generation " + generation + ").");
                    }
                }
            } else if (throwable != null) {
                logger.warning("Page fetch " + apiPage + " failed: " + throwable);
            }

            clearFetchStateAndNotify();
        });
        return true;
    }

    private void clearFetchStateAndNotify() {
        Runnable callback;
        synchronized (this) {
            callback = onDataLoaded;
            onDataLoaded = null;
        }
        isFetching.set(false);
        if (callback != null) {
            callback.run();
        }
    }

    public boolean isFetching() {
        return isFetching.get();
    }

    public static class FetchResult<T> {
        private final List<T> items;
        private final int totalCount;
        private final boolean success;

        public FetchResult(List<T> items, int totalCount) {
            this(items, totalCount, true);
        }

        public FetchResult(List<T> items, int totalCount, boolean success) {
            this.items = items;
            this.totalCount = totalCount;
            this.success = success;
        }

        public List<T> items() { return this.items; }
        public int totalCount() { return this.totalCount; }
        public boolean success() { return this.success; }
    }
}
