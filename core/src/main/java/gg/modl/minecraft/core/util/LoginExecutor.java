package gg.modl.minecraft.core.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoginExecutor implements AutoCloseable {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

    private final ThreadPoolExecutor executor;

    public LoginExecutor(String threadNamePrefix, int maxThreads, int queueCapacity) {
        if (maxThreads < 1) throw new IllegalArgumentException("maxThreads must be positive");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");

        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                maxThreads,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadNamePrefix + "-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public CompletableFuture<Void> runAsync(Runnable task) throws RejectedExecutionException {
        return CompletableFuture.runAsync(task, executor);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
