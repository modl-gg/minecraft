package io.github._4drian3d.signedvelocity.common.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueuedDataTest {
    @Test
    void acceptNextResultAppliesResultOnWaitingThread() throws Exception {
        QueuedData data = new QueuedData();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        AtomicReference<Thread> applyingThread = new AtomicReference<>();

        Thread listenerThread = new Thread(() -> {
            waitingThread.set(Thread.currentThread());
            waiting.countDown();
            data.acceptNextResult(result -> applyingThread.set(Thread.currentThread()));
        }, "listener-thread");

        listenerThread.start();
        assertTrue(waiting.await(1, TimeUnit.SECONDS));

        data.complete(SignedResult.cancel());
        listenerThread.join(1_000L);

        assertSame(waitingThread.get(), applyingThread.get());
    }

    @Test
    void completeCancelsPendingTimeoutTask() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> future = data.nextResult();
        ScheduledTask timeout = scheduler.onlyTask();

        data.complete(SignedResult.cancel());

        assertSame(SignedResult.cancel(), future.get(1, TimeUnit.SECONDS));
        assertTrue(timeout.isCancelled());
        assertEquals(0, data.pendingWaiters());
    }

    @Test
    void timedOutPendingResultIsRemovedBeforeLaterCompletion() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> timedOut = data.nextResult();
        scheduler.onlyTask().run();

        assertSame(SignedResult.allowed(), timedOut.get(1, TimeUnit.SECONDS));
        assertEquals(0, data.pendingWaiters());

        data.complete(SignedResult.cancel());

        Future<SignedResult> next = data.nextResult();
        assertSame(SignedResult.cancel(), next.get(1, TimeUnit.SECONDS));
    }

    @Test
    void dataFromReturnsSingleQueuedDataForConcurrentCallers() throws Exception {
        SignedQueue queue = new SignedQueue();
        UUID uuid = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<QueuedData>> calls = IntStream.range(0, 64)
                .mapToObj(ignored -> (Callable<QueuedData>) () -> {
                    assertTrue(start.await(1, TimeUnit.SECONDS));
                    return queue.dataFrom(uuid);
                })
                .collect(Collectors.toList());

        List<Future<QueuedData>> futures = calls.stream()
                .map(executor::submit)
                .collect(Collectors.toList());
        start.countDown();

        Set<QueuedData> results = futures.stream()
                .map(QueuedDataTest::getUnchecked)
                .collect(Collectors.toSet());

        executor.shutdownNow();
        assertEquals(1, results.size());
    }

    @Test
    void closingQueuedDataCompletesPendingWaitersWithAllowedAndClearsState() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> future = data.nextResult();
        ScheduledTask timeout = scheduler.onlyTask();

        data.close();

        assertSame(SignedResult.allowed(), future.get(1, TimeUnit.SECONDS));
        assertTrue(timeout.isCancelled());
        assertEquals(0, data.pendingWaiters());
        assertSame(SignedResult.allowed(), data.nextResult().get(1, TimeUnit.SECONDS));
    }

    @Test
    void closingSignedQueueClosesAllQueuedDataAndClearsMap() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        SignedQueue queue = new SignedQueue(scheduler, 20);
        UUID uuid = UUID.randomUUID();
        QueuedData data = queue.dataFrom(uuid);
        Future<SignedResult> future = data.nextResult();

        queue.close();

        assertSame(SignedResult.allowed(), future.get(1, TimeUnit.SECONDS));
        assertEquals(0, data.pendingWaiters());
        assertEquals(0, queue.queuedDataCount());
        assertSame(SignedResult.allowed(), queue.dataFrom(uuid).nextResult().get(1, TimeUnit.SECONDS));
    }

    @Test
    void completeSatisfiesAdvancingWaiterEvenWhenNonAdvancingWaiterIsAheadInQueue() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> peek = data.nextResultWithoutAdvance();
        Future<SignedResult> consume = data.nextResult();
        assertEquals(2, data.pendingWaiters());

        data.complete(SignedResult.cancel());

        assertSame(SignedResult.cancel(), peek.get(1, TimeUnit.SECONDS));
        assertSame(SignedResult.cancel(), consume.get(1, TimeUnit.SECONDS));
        assertEquals(0, data.pendingWaiters());
        data.nextResult();
        assertEquals(1, data.pendingWaiters());
    }

    @Test
    void singleAdvancingWaiterReceivesVerdictWithNoShiftByOne() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> consume = data.nextResult();
        data.complete(SignedResult.cancel());

        assertSame(SignedResult.cancel(), consume.get(1, TimeUnit.SECONDS));
        assertEquals(0, data.pendingWaiters());
        data.nextResult();
        assertEquals(1, data.pendingWaiters());
    }

    @Test
    void nonAdvancingPeekLeavesResultForFollowingAdvancingRead() throws Exception {
        TestScheduler scheduler = new TestScheduler();
        QueuedData data = new QueuedData(scheduler, 20);

        Future<SignedResult> peek = data.nextResultWithoutAdvance();
        SignedResult modify = SignedResult.modify("x");
        data.complete(modify);

        // Peek waiter resolves to the modify result...
        assertSame(modify, peek.get(1, TimeUnit.SECONDS));
        // ...and a following advancing read still observes the same modify result (peek-leaves-value invariant).
        assertSame(modify, data.nextResult().get(1, TimeUnit.SECONDS));
    }

    @Test
    void cancelledMessageStillAdvancesQueueSoNextMessageGetsItsOwnVerdict() {
        QueuedData data = new QueuedData();
        data.complete(SignedResult.cancel());
        SignedResult clean = SignedResult.modify("clean text");
        data.complete(clean);

        data.acceptNextResult(result -> { /* msg #1 cancelled by another plugin: discard */ });

        AtomicReference<SignedResult> applied = new AtomicReference<>();
        data.acceptNextResult(applied::set);
        assertEquals("clean text", applied.get().toModify());
        assertEquals(0, data.pendingWaiters());
    }

    private static QueuedData getUnchecked(Future<QueuedData> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class TestScheduler implements ScheduledExecutorService {
        private final List<ScheduledTask> tasks = new ArrayList<>();

        ScheduledTask onlyTask() {
            assertEquals(1, tasks.size());
            return tasks.get(0);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            ScheduledTask task = new ScheduledTask(command);
            tasks.add(task);
            return task;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ScheduledTask implements RunnableScheduledFuture<Void> {
        private final Runnable command;
        private boolean cancelled;
        private boolean done;

        private ScheduledTask(Runnable command) {
            this.command = command;
        }

        @Override
        public boolean isPeriodic() {
            return false;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public void run() {
            if (!cancelled) {
                command.run();
            }
            done = true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
