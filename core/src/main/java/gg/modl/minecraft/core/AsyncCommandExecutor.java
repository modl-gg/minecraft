package gg.modl.minecraft.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class AsyncCommandExecutor {
    private static final int MAX_THREADS = 4;
    private static final int QUEUE_CAPACITY = 64;
    private static final Logger LOGGER = Logger.getLogger(AsyncCommandExecutor.class.getName());

    private final ThreadPoolExecutor executor;
    private final Set<String> asyncCommandAliases;

    public AsyncCommandExecutor() {
        this.executor = new ThreadPoolExecutor(
                MAX_THREADS, MAX_THREADS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "modl-AsyncCmd");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
        this.asyncCommandAliases = ConcurrentHashMap.newKeySet();
    }

    public void registerAsyncAlias(String alias) {
        asyncCommandAliases.add(alias.toLowerCase());
    }

    public boolean isAsyncCommand(String baseCommand) {
        return asyncCommandAliases.contains(baseCommand.toLowerCase());
    }

    public void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            LOGGER.warning("Async command executor saturated (queue+pool full); dropped a queued command task rather than blocking the calling thread.");
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
