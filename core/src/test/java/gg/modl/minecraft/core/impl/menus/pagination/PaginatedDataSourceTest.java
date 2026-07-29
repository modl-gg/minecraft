package gg.modl.minecraft.core.impl.menus.pagination;

import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource.FetchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedDataSourceTest {
    @Test
    void fetchPageReturnsFalseWhenAnotherFetchIsAlreadyRunning() {
        AtomicInteger fetches = new AtomicInteger();
        CompletableFuture<FetchResult<Integer>> pendingFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> {
            fetches.incrementAndGet();
            return pendingFetch;
        });
        dataSource.initialize(Arrays.asList(1, 2), 4);

        assertTrue(dataSource.fetchPage(2, null));
        assertFalse(dataSource.fetchPage(2, null));

        pendingFetch.complete(new FetchResult<>(Arrays.asList(3, 4), 4));

        assertEquals(1, fetches.get());
        assertEquals(Arrays.asList(1, 2, 3, 4), dataSource.getAllLoadedItems());
    }

    @Test
    void inFlightFetchRunsOnlyLatestAttachedRefreshCallback() {
        AtomicInteger firstRefreshes = new AtomicInteger();
        AtomicInteger secondRefreshes = new AtomicInteger();
        CompletableFuture<FetchResult<Integer>> pendingFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> pendingFetch);
        dataSource.initialize(Collections.singletonList(1), 3);

        assertTrue(dataSource.fetchPage(2, firstRefreshes::incrementAndGet));
        dataSource.setOnDataLoaded(secondRefreshes::incrementAndGet);

        pendingFetch.complete(new FetchResult<>(Arrays.asList(2, 3), 3));

        assertEquals(0, firstRefreshes.get());
        assertEquals(1, secondRefreshes.get());
    }

    @Test
    void staleCallbackDoesNotOverwriteFreshPage() throws InterruptedException {
        CompletableFuture<FetchResult<Integer>> stalePageFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> stalePageFetch);
        dataSource.initialize(Arrays.asList(1, 2), 4);

        CountDownLatch callbackCompleted = new CountDownLatch(1);
        assertTrue(dataSource.fetchPage(2, callbackCompleted::countDown));

        dataSource.initialize(Arrays.asList(5, 6, 7, 8), 8);

        stalePageFetch.complete(new FetchResult<>(Arrays.asList(3, 4), 4));
        assertTrue(callbackCompleted.await(1, TimeUnit.SECONDS));

        assertEquals(Arrays.asList(5, 6, 7, 8), dataSource.getAllLoadedItems());
        assertEquals(8, dataSource.getTotalCount());
        assertEquals(4, dataSource.getTotalMenuPages());
    }

    @Test
    void failedFetchDoesNotZeroTotalCount() {
        CompletableFuture<FetchResult<Integer>> pendingFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> pendingFetch);
        dataSource.initialize(Arrays.asList(1, 2), 6);

        assertTrue(dataSource.fetchPage(2, null));
        pendingFetch.complete(failedFetchResult());

        assertEquals(Arrays.asList(1, 2), dataSource.getAllLoadedItems());
        assertEquals(6, dataSource.getTotalCount());
        assertEquals(3, dataSource.getTotalMenuPages());
    }

    @Test
    void failedFetchStillRunsRefreshCallback() throws InterruptedException {
        CompletableFuture<FetchResult<Integer>> pendingFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> pendingFetch);
        dataSource.initialize(Arrays.asList(1, 2), 6);

        CountDownLatch callbackCompleted = new CountDownLatch(1);
        assertTrue(dataSource.fetchPage(2, callbackCompleted::countDown));

        pendingFetch.completeExceptionally(new RuntimeException("network down"));

        assertTrue(callbackCompleted.await(1, TimeUnit.SECONDS));
        assertFalse(dataSource.isFetching());
        assertEquals(6, dataSource.getTotalCount());
    }

    @Test
    void fetchedItemsAppendInPageOrderWithoutDuplicates() {
        CompletableFuture<FetchResult<Integer>> pendingFetch = new CompletableFuture<>();
        PaginatedDataSource<Integer> dataSource = new PaginatedDataSource<>(2, (page, limit) -> pendingFetch);
        dataSource.initialize(Arrays.asList(1, 2), 4);

        assertTrue(dataSource.fetchPage(2, null));
        pendingFetch.complete(new FetchResult<>(Arrays.asList(3, 4), 4));

        List<Integer> loadedItems = dataSource.getAllLoadedItems();
        assertEquals(Arrays.asList(1, 2, 3, 4), loadedItems);
    }

    private static FetchResult<Integer> failedFetchResult() {
        return new FetchResult<>(Collections.emptyList(), 0, false);
    }
}
