package gg.modl.minecraft.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages async command execution for Spigot and BungeeCord platforms.
 * Commands registered as async will be dispatched off the main/network thread
 * to avoid blocking on I/O operations (HTTP calls for player lookups, etc.).
 * <p>
 * Uses a bounded thread pool (4 threads) backed by a bounded queue (up to 64).
 * Transient bursts beyond 4 concurrent commands queue on worker threads. If the
 * queue also fills under sustained overload, new tasks are dropped (and logged)
 * rather than executed on the calling thread, preserving the guarantee that
 * blocking I/O never runs on the main/network thread. Idle threads are reclaimed
 * after the 60s keep-alive, so idle footprint stays minimal.
 */
public class AsyncCommandExecutor {
    /**
     * Max 4 threads, commands are I/O-bound (HTTP calls ~50-500ms), so threads
     * spend most time blocked, not competing for CPU. 4 concurrent commands is
     * well above typical peak usage on any Minecraft server, and keeps thread
     * stack memory (~1MB each) and context switching costs negligible.
     */
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

    /**
     * Register a command alias for async execution.
     * All aliases (pipe-separated in ACF's @CommandAlias) should be registered individually.
     */
    public void registerAsyncAlias(String alias) {
        asyncCommandAliases.add(alias.toLowerCase());
    }

    /**
     * Check if a base command name should be executed asynchronously.
     */
    public boolean isAsyncCommand(String baseCommand) {
        return asyncCommandAliases.contains(baseCommand.toLowerCase());
    }

    /**
     * Submit a command for async execution.
     */
    public void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            LOGGER.warning("Async command executor saturated (queue+pool full); dropped a queued command task rather than blocking the calling thread.");
        }
    }

    /**
     * Shut down the executor. Called on plugin disable.
     */
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
