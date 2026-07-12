package gg.modl.minecraft.core.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class BoundedLookupExecutor {
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private final String threadNamePrefix;
    private final int coreThreads;
    private final int maxThreads;
    private final int queueCapacity;
    private final boolean coreThreadTimeOut;
    private final Object lock = new Object();
    private volatile ThreadPoolExecutor pool;

    public BoundedLookupExecutor(String threadNamePrefix, int coreThreads, int maxThreads, int queueCapacity,
                                 boolean coreThreadTimeOut) {
        this.threadNamePrefix = threadNamePrefix;
        this.coreThreads = coreThreads;
        this.maxThreads = maxThreads;
        this.queueCapacity = queueCapacity;
        this.coreThreadTimeOut = coreThreadTimeOut;
        this.pool = newDaemonPool(threadNamePrefix, coreThreads, maxThreads, queueCapacity, coreThreadTimeOut);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, liveExecutor());
    }

    public void shutdown() {
        ThreadPoolExecutor previous;
        synchronized (lock) {
            previous = pool;
            pool = newDaemonPool(threadNamePrefix, coreThreads, maxThreads, queueCapacity, coreThreadTimeOut);
        }
        shutdown(previous, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    private ThreadPoolExecutor liveExecutor() {
        ThreadPoolExecutor current = pool;
        if (!current.isShutdown() && !current.isTerminated()) return current;
        synchronized (lock) {
            if (pool.isShutdown() || pool.isTerminated()) {
                pool = newDaemonPool(threadNamePrefix, coreThreads, maxThreads, queueCapacity, coreThreadTimeOut);
            }
            return pool;
        }
    }

    public static ThreadPoolExecutor newDaemonPool(String threadNamePrefix, int coreThreads, int maxThreads,
                                            int queueCapacity, boolean coreThreadTimeOut) {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadNamePrefix + "-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        if (coreThreadTimeOut) executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static void shutdown(ThreadPoolExecutor executor, long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
