package io.github._4drian3d.signedvelocity.common.queue;

import io.github._4drian3d.signedvelocity.shared.PropertyHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class QueuedData {
    private static final int timeout = PropertyHolder.readInt("io.github._4drian3d.signedvelocity.timeout", 20);
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SignedVelocity-Timeout");
                t.setDaemon(true);
                return t;
            });

    private final Queue<SignedResult> results = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<SignedResult>> unSyncronizedQueue = new ConcurrentLinkedQueue<>();

    public void complete(final SignedResult result) {
        this.results.add(result);
        CompletableFuture<SignedResult> unSynchronized = unSyncronizedQueue.poll();
        if (unSynchronized != null) {
            unSynchronized.complete(result);
        }
    }

    public CompletableFuture<SignedResult> nextResult() {
        SignedResult result = results.poll();
        return futureFrom(result);
    }

    public CompletableFuture<SignedResult> nextResultWithoutAdvance() {
        SignedResult result = results.peek();
        return futureFrom(result);
    }

    private CompletableFuture<SignedResult> futureFrom(@Nullable final SignedResult result) {
        if (result == null) {
            final CompletableFuture<SignedResult> future = new CompletableFuture<>();
            TIMEOUT_EXECUTOR.schedule(() -> future.complete(SignedResult.allowed()), timeout, TimeUnit.MILLISECONDS);
            unSyncronizedQueue.add(future);
            return future;
        } else {
            return CompletableFuture.completedFuture(result);
        }
    }
}
