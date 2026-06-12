package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.ChatLogBatchRequest;
import gg.modl.minecraft.api.http.request.CommandLogBatchRequest;
import gg.modl.minecraft.api.http.request.SyncRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Owns the upload of buffered chat and command logs to the dedicated HTTP batch endpoints,
 * decoupled from the (now removed) sync poll. A short flush timer drains the
 * {@link ChatCommandLogService} buffers and posts whatever has accumulated.
 *
 * <p>Invalid usernames are dropped before upload (the backend applies the same {@code @Pattern}
 * validation and would reject the whole batch otherwise). On a flush failure the drained entries
 * are re-buffered, bounded so a sustained backend outage cannot grow memory without limit.</p>
 */
public class LogUploadService {
    private static final long FLUSH_INTERVAL_SECONDS = 3;
    private static final long SHUTDOWN_DRAIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_REBUFFERED_ENTRIES = 5000;

    private final HttpClientHolder httpClientHolder;
    private final ChatCommandLogService chatCommandLogService;
    private final PluginLogger logger;
    private final boolean debugMode;

    private final List<SyncRequest.ChatLogEntry> pendingChat = new ArrayList<>();
    private final List<SyncRequest.CommandLogEntry> pendingCommand = new ArrayList<>();

    private volatile ScheduledExecutorService executor;
    private volatile boolean running = false;

    public LogUploadService(HttpClientHolder httpClientHolder, ChatCommandLogService chatCommandLogService,
                            PluginLogger logger, boolean debugMode) {
        this.httpClientHolder = httpClientHolder;
        this.chatCommandLogService = chatCommandLogService;
        this.logger = logger;
        this.debugMode = debugMode;
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
            // Stop the periodic flusher and let any in-progress scheduled flush finish first, so the
            // final drain below sees a quiescent buffer and there is no concurrent flush.
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

    /**
     * Final drain on the calling (shutdown) thread: flush whatever remains and block until the
     * in-flight upload futures settle, bounded by {@link #SHUTDOWN_DRAIN_TIMEOUT_SECONDS}, so the last
     * chat/command batch is not lost when {@code flush()} only enqueues async HTTP futures.
     */
    private void drainOnShutdown() {
        List<CompletableFuture<Void>> uploads = flush();
        if (uploads.isEmpty()) return;
        try {
            CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0]))
                .get(SHUTDOWN_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warning("Timed out draining buffered logs on shutdown; final batch may be incomplete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Per-upload failures already re-buffer/log via the future's exceptionally handler.
            if (debugMode) logger.info("Shutdown log drain completed with errors: " + e.getMessage());
        }
    }

    private List<CompletableFuture<Void>> flush() {
        List<CompletableFuture<Void>> uploads = new ArrayList<>();
        try {
            List<SyncRequest.ChatLogEntry> chat = SyncService.filterByUsername(
                drainChat(), SyncRequest.ChatLogEntry::getUsername);
            List<SyncRequest.CommandLogEntry> command = SyncService.filterByUsername(
                drainCommand(), SyncRequest.CommandLogEntry::getUsername);

            if (!chat.isEmpty()) uploads.add(flushChat(chat));
            if (!command.isEmpty()) uploads.add(flushCommand(command));
        } catch (Exception e) {
            logger.warning("Log flush failed: " + e.getMessage());
        }
        return uploads;
    }

    private List<SyncRequest.ChatLogEntry> drainChat() {
        List<SyncRequest.ChatLogEntry> drained = chatCommandLogService.drainChatBuffer();
        synchronized (pendingChat) {
            if (pendingChat.isEmpty()) return drained;
            List<SyncRequest.ChatLogEntry> combined = new ArrayList<>(pendingChat);
            pendingChat.clear();
            combined.addAll(drained);
            return combined;
        }
    }

    private List<SyncRequest.CommandLogEntry> drainCommand() {
        List<SyncRequest.CommandLogEntry> drained = chatCommandLogService.drainCommandBuffer();
        synchronized (pendingCommand) {
            if (pendingCommand.isEmpty()) return drained;
            List<SyncRequest.CommandLogEntry> combined = new ArrayList<>(pendingCommand);
            pendingCommand.clear();
            combined.addAll(drained);
            return combined;
        }
    }

    private CompletableFuture<Void> flushChat(List<SyncRequest.ChatLogEntry> chat) {
        ChatLogBatchRequest request = new ChatLogBatchRequest(chat.stream()
            .map(e -> new ChatLogBatchRequest.ChatLogEntry(e.getUuid(), e.getUsername(), e.getMessage(), e.getServer(), e.getTimestamp()))
            .collect(Collectors.toList()));
        return httpClientHolder.getClient().submitChatLogs(request).exceptionally(throwable -> {
            rebufferChat(chat, throwable);
            return null;
        });
    }

    private CompletableFuture<Void> flushCommand(List<SyncRequest.CommandLogEntry> command) {
        CommandLogBatchRequest request = new CommandLogBatchRequest(command.stream()
            .map(e -> new CommandLogBatchRequest.CommandLogEntry(e.getUuid(), e.getUsername(), e.getCommand(), e.getServer(), e.getTimestamp()))
            .collect(Collectors.toList()));
        return httpClientHolder.getClient().submitCommandLogs(request).exceptionally(throwable -> {
            rebufferCommand(command, throwable);
            return null;
        });
    }

    private void rebufferChat(List<SyncRequest.ChatLogEntry> chat, Throwable throwable) {
        warnFlushFailure("chat", throwable);
        synchronized (pendingChat) {
            pendingChat.addAll(0, chat);
            dropOldest(pendingChat, "chat");
        }
    }

    private void rebufferCommand(List<SyncRequest.CommandLogEntry> command, Throwable throwable) {
        warnFlushFailure("command", throwable);
        synchronized (pendingCommand) {
            pendingCommand.addAll(0, command);
            dropOldest(pendingCommand, "command");
        }
    }

    // Buffers keep entries oldest-first; on overflow we drop from the head (oldest) so the
    // most recent activity is preserved for the next flush.

    private void warnFlushFailure(String kind, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            logger.warning("Failed to upload " + kind + " logs: Panel temporarily unavailable; re-buffering");
        } else {
            logger.warning("Failed to upload " + kind + " logs: " + cause.getMessage() + "; re-buffering");
        }
    }

    private void dropOldest(List<?> buffer, String kind) {
        int overflow = buffer.size() - MAX_REBUFFERED_ENTRIES;
        if (overflow > 0) {
            buffer.subList(0, overflow).clear();
            logger.warning("Dropped " + overflow + " buffered " + kind + " logs (re-buffer cap " + MAX_REBUFFERED_ENTRIES + " reached)");
        }
    }
}
