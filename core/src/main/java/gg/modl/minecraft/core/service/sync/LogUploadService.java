package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.ChatLogEntry;
import gg.modl.minecraft.api.http.CommandLogEntry;
import gg.modl.minecraft.api.http.request.ChatLogBatchRequest;
import gg.modl.minecraft.api.http.request.CommandLogBatchRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LogUploadService {
    private static final long FLUSH_INTERVAL_SECONDS = 3;
    private static final long SHUTDOWN_DRAIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_REBUFFERED_ENTRIES = 5000;
    private static final int MAX_BATCH_SIZE = 500;

    private final PluginLogger logger;
    private final boolean debugMode;
    private final BufferedLogChannel<ChatLogEntry> chatChannel;
    private final BufferedLogChannel<CommandLogEntry> commandChannel;

    private volatile ScheduledExecutorService executor;
    private volatile boolean running = false;

    public LogUploadService(HttpClientHolder httpClientHolder, ChatCommandLogService chatCommandLogService,
                            PluginLogger logger, boolean debugMode) {
        this.logger = logger;
        this.debugMode = debugMode;
        this.chatChannel = new BufferedLogChannel<>("chat", chatCommandLogService::drainChatBuffer,
                ChatLogEntry::getUsername, ChatLogEntry::getMessage,
                entries -> httpClientHolder.getClient().submitChatLogs(new ChatLogBatchRequest(entries)),
                logger, MAX_REBUFFERED_ENTRIES, MAX_BATCH_SIZE);
        this.commandChannel = new BufferedLogChannel<>("command", chatCommandLogService::drainCommandBuffer,
                CommandLogEntry::getUsername, CommandLogEntry::getCommand,
                entries -> httpClientHolder.getClient().submitCommandLogs(new CommandLogBatchRequest(entries)),
                logger, MAX_REBUFFERED_ENTRIES, MAX_BATCH_SIZE);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-log-upload");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (debugMode) logger.info("Log upload service started (flush every " + FLUSH_INTERVAL_SECONDS + "s)");
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        drainOnShutdown();
    }

    private void drainOnShutdown() {
        try {
            CompletableFuture.allOf(flush().toArray(new CompletableFuture[0]))
                .get(SHUTDOWN_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warning("Timed out draining buffered logs on shutdown; final batch may be incomplete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (debugMode) logger.info("Shutdown log drain completed with errors: " + e.getMessage());
        }
    }

    private List<CompletableFuture<Void>> flush() {
        return Arrays.asList(chatChannel.flush(), commandChannel.flush());
    }
}
