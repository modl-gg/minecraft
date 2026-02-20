package gg.modl.minecraft.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manages async command execution for Spigot and BungeeCord platforms.
 * Commands registered as async will be dispatched off the main/network thread
 * to avoid blocking on I/O operations (HTTP calls for player lookups, etc.).
 *
 * Uses a bounded cached thread pool: threads are created on demand, idle threads
 * are reclaimed after 60 seconds, and the pool is capped at 4 threads.
 * This is sufficient for Minecraft command throughput (rarely more than a few
 * concurrent commands) while keeping context switching and memory overhead minimal.
 */
public class AsyncCommandExecutor {
    /**
     * Max 4 threads — commands are I/O-bound (HTTP calls ~50-500ms), so threads
     * spend most time blocked, not competing for CPU. 4 concurrent commands is
     * well above typical peak usage on any Minecraft server, and keeps thread
     * stack memory (~1MB each) and context switching costs negligible.
     */
    private static final int MAX_THREADS = 4;

    private final ThreadPoolExecutor executor;
    private final Set<String> asyncCommandAliases;

    public AsyncCommandExecutor() {
        this.executor = new ThreadPoolExecutor(
                0, MAX_THREADS,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "MODL-AsyncCmd");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.asyncCommandAliases = new HashSet<>();
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
     * Get an unmodifiable view of the registered async command aliases.
     */
    public Set<String> getAsyncAliases() {
        return Collections.unmodifiableSet(asyncCommandAliases);
    }

    /**
     * Submit a command for async execution.
     */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /**
     * Shut down the executor. Called on plugin disable.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
