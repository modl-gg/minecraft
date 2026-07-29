package gg.modl.minecraft.core.login;

import gg.modl.minecraft.core.util.BoundedLookupExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

public final class LoginExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;

    public LoginExecutor(String threadNamePrefix, int maxThreads, int queueCapacity) {
        if (maxThreads < 1) throw new IllegalArgumentException("maxThreads must be positive");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");
        this.executor = BoundedLookupExecutor.newDaemonPool(threadNamePrefix, maxThreads, maxThreads, queueCapacity, true);
    }

    public CompletableFuture<Void> runAsync(Runnable task) throws RejectedExecutionException {
        return CompletableFuture.runAsync(task, executor);
    }

    public void shutdown() {
        BoundedLookupExecutor.shutdown(executor, BoundedLookupExecutor.DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    @Override
    public void close() {
        shutdown();
    }
}
