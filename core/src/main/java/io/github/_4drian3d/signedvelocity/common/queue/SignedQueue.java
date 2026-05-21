package io.github._4drian3d.signedvelocity.common.queue;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public final class SignedQueue implements AutoCloseable {
    private final Map<UUID, QueuedData> signedResults = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor;
    private final int timeoutMillis;
    private final boolean shutdownExecutor;
    private volatile boolean closed;

    public SignedQueue() {
        this(QueuedData.createTimeoutExecutor(), QueuedData.timeoutMillis(), true);
    }

    SignedQueue(final ScheduledExecutorService timeoutExecutor, final int timeoutMillis) {
        this(timeoutExecutor, timeoutMillis, false);
    }

    private SignedQueue(
            final ScheduledExecutorService timeoutExecutor,
            final int timeoutMillis,
            final boolean shutdownExecutor
    ) {
        this.timeoutExecutor = timeoutExecutor;
        this.timeoutMillis = timeoutMillis;
        this.shutdownExecutor = shutdownExecutor;
    }

    public synchronized @NotNull QueuedData dataFrom(final @NotNull UUID uuid) {
        if (closed) {
            return QueuedData.closedData();
        }
        return signedResults.computeIfAbsent(uuid, ignored -> new QueuedData(timeoutExecutor, timeoutMillis));
    }

    public void removeData(final UUID uuid) {
        this.signedResults.remove(uuid);
    }

    @Override
    public synchronized void close() {
        closed = true;
        signedResults.values().forEach(QueuedData::close);
        signedResults.clear();
        if (shutdownExecutor) {
            timeoutExecutor.shutdownNow();
        }
    }

    int queuedDataCount() {
        return signedResults.size();
    }
}
