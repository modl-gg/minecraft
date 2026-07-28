package io.github._4drian3d.signedvelocity.common.queue;

import io.github._4drian3d.signedvelocity.shared.PropertyHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class QueuedData implements AutoCloseable {
    private static final int timeout = PropertyHolder.readInt("io.github._4drian3d.signedvelocity.timeout", 20);
    private static final long WAIT_GRACE_MILLIS = 50L;
    private static final QueuedData CLOSED_DATA = new QueuedData(null, timeout, false, true);

    private final Queue<SignedResult> results = new ConcurrentLinkedQueue<>();
    private final Queue<PendingResult> unSyncronizedQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService timeoutExecutor;
    private final int timeoutMillis;
    private final boolean shutdownExecutor;
    private volatile boolean closed;

    public QueuedData() {
        this(createTimeoutExecutor(), timeout, true, false);
    }

    QueuedData(final ScheduledExecutorService timeoutExecutor, final int timeoutMillis) {
        this(timeoutExecutor, timeoutMillis, false, false);
    }

    private QueuedData(
            final ScheduledExecutorService timeoutExecutor,
            final int timeoutMillis,
            final boolean shutdownExecutor,
            final boolean closed
    ) {
        this.timeoutExecutor = timeoutExecutor;
        this.timeoutMillis = timeoutMillis;
        this.shutdownExecutor = shutdownExecutor;
        this.closed = closed;
    }

    public void complete(final SignedResult result) {
        if (closed) {
            return;
        }
        // Satisfy every pending non-advancing (peek) waiter and 1 advancing (consume) waiter,
        // since a single proxy result consumes exactly one message. Store the result only if no advancing
        // waiter consumed it (peekers/nobody were waiting), preserving the single-message decorate -> chat flow.
        boolean consumedByAdvancing = false;
        PendingResult pending;
        while ((pending = unSyncronizedQueue.poll()) != null) {
            if (!pending.complete(result)) {
                continue;
            }
            if (pending.advance()) {
                consumedByAdvancing = true;
                break;
            }
        }
        if (!consumedByAdvancing) {
            this.results.add(result);
        }
    }

    public CompletableFuture<SignedResult> nextResult() {
        if (closed) {
            return CompletableFuture.completedFuture(SignedResult.allowed());
        }
        SignedResult result = results.poll();
        return futureFrom(result);
    }

    public CompletableFuture<SignedResult> nextResultWithoutAdvance() {
        if (closed) {
            return CompletableFuture.completedFuture(SignedResult.allowed());
        }
        SignedResult result = results.peek();
        if (result == null) {
            return registerPendingResult(false);
        } else {
            return CompletableFuture.completedFuture(result);
        }
    }

    public void acceptNextResult(final Consumer<SignedResult> consumer) {
        consumer.accept(awaitResult(nextResult()));
    }

    public void acceptNextResultWithoutAdvance(final Consumer<SignedResult> consumer) {
        consumer.accept(awaitResult(nextResultWithoutAdvance()));
    }

    private CompletableFuture<SignedResult> futureFrom(@Nullable final SignedResult result) {
        if (result == null) {
            return registerPendingResult(true);
        } else {
            return CompletableFuture.completedFuture(result);
        }
    }

    private CompletableFuture<SignedResult> registerPendingResult(final boolean advance) {
        if (closed) {
            return CompletableFuture.completedFuture(SignedResult.allowed());
        }
        final CompletableFuture<SignedResult> future = new CompletableFuture<>();
        final PendingResult pending = new PendingResult(future, advance);
        unSyncronizedQueue.add(pending);
        if (closed) {
            if (unSyncronizedQueue.remove(pending)) {
                pending.complete(SignedResult.allowed());
            }
            return future;
        }

        final SignedResult raced = results.poll();
        if (raced != null) {
            if (unSyncronizedQueue.remove(pending)) {
                pending.complete(raced);
                if (!advance) {
                    results.add(raced);
                }
            } else {
                results.add(raced);
            }
            return future;
        }
        try {
            final ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(
                    () -> pending.timeout(unSyncronizedQueue),
                    timeoutMillis,
                    TimeUnit.MILLISECONDS
            );
            pending.timeoutTask(timeoutTask);
        } catch (RejectedExecutionException exception) {
            if (unSyncronizedQueue.remove(pending)) {
                pending.complete(SignedResult.allowed());
            }
        }
        return future;
    }

    private SignedResult awaitResult(final CompletableFuture<SignedResult> future) {
        try {
            return future.get(timeoutMillis + WAIT_GRACE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SignedResult.allowed();
        } catch (ExecutionException | TimeoutException exception) {
            return SignedResult.allowed();
        }
    }

    int pendingWaiters() {
        return unSyncronizedQueue.size();
    }

    @Override
    public void close() {
        closed = true;
        results.clear();

        PendingResult pending;
        while ((pending = unSyncronizedQueue.poll()) != null) {
            pending.complete(SignedResult.allowed());
        }

        if (shutdownExecutor && timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
        }
    }

    static QueuedData closedData() {
        return CLOSED_DATA;
    }

    static int timeoutMillis() {
        return timeout;
    }

    static ScheduledExecutorService createTimeoutExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SignedVelocity-Timeout");
            t.setDaemon(true);
            return t;
        });
    }

    private static final class PendingResult {
        private final CompletableFuture<SignedResult> future;
        private final boolean advance;
        private volatile ScheduledFuture<?> timeoutTask;

        private PendingResult(final CompletableFuture<SignedResult> future, final boolean advance) {
            this.future = future;
            this.advance = advance;
        }

        private boolean advance() {
            return advance;
        }

        private void timeoutTask(final ScheduledFuture<?> timeoutTask) {
            this.timeoutTask = timeoutTask;
            if (future.isDone()) {
                timeoutTask.cancel(false);
            }
        }

        private boolean complete(final SignedResult result) {
            final boolean completed = future.complete(result);
            if (completed) {
                final ScheduledFuture<?> task = timeoutTask;
                if (task != null) {
                    task.cancel(false);
                }
            }
            return completed;
        }

        private void timeout(final Queue<PendingResult> queue) {
            if (future.complete(SignedResult.allowed())) {
                queue.remove(this);
            }
        }
    }
}
