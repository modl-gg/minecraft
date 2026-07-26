package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.ApiClientException;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

final class BufferedLogChannel<T> {
    private final String kind;
    private final Supplier<List<T>> drainSource;
    private final Function<T, String> usernameAccessor;
    private final Function<T, String> contentAccessor;
    private final Function<List<T>, CompletableFuture<Void>> submit;
    private final PluginLogger logger;
    private final int maxRebuffered;
    private final int maxBatchSize;
    private final List<T> pending = new ArrayList<>();

    BufferedLogChannel(String kind, Supplier<List<T>> drainSource, Function<T, String> usernameAccessor,
                       Function<T, String> contentAccessor, Function<List<T>, CompletableFuture<Void>> submit,
                       PluginLogger logger, int maxRebuffered, int maxBatchSize) {
        this.kind = kind;
        this.drainSource = drainSource;
        this.usernameAccessor = usernameAccessor;
        this.contentAccessor = contentAccessor;
        this.submit = submit;
        this.logger = logger;
        this.maxRebuffered = maxRebuffered;
        this.maxBatchSize = maxBatchSize;
    }

    CompletableFuture<Void> flush() {
        try {
            return uploadDrainedEntries();
        } catch (Exception e) {
            logger.warning("Failed to collect " + kind + " logs for upload: " + e.getMessage());
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private CompletableFuture<Void> uploadDrainedEntries() {
        List<T> entries = sanitize(SyncService.filterByUsername(drain(), usernameAccessor));
        if (entries.isEmpty()) return CompletableFuture.completedFuture(null);

        List<CompletableFuture<Void>> chunkUploads = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += maxBatchSize) {
            List<T> chunk = new ArrayList<>(entries.subList(start, Math.min(entries.size(), start + maxBatchSize)));
            chunkUploads.add(submitChunk(chunk));
        }
        return CompletableFuture.allOf(chunkUploads.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> submitChunk(List<T> chunk) {
        return submit.apply(chunk).exceptionally(throwable -> {
            handleUploadFailure(chunk, throwable);
            return null;
        });
    }

    private List<T> sanitize(List<T> entries) {
        List<T> retained = new ArrayList<>(entries.size());
        for (T entry : entries) {
            if (!StringUtil.isBlank(contentAccessor.apply(entry))) retained.add(entry);
        }
        return retained;
    }

    private List<T> drain() {
        List<T> drained = drainSource.get();
        synchronized (pending) {
            if (pending.isEmpty()) return drained;
            List<T> combined = new ArrayList<>(pending);
            pending.clear();
            combined.addAll(drained);
            return combined;
        }
    }

    private void handleUploadFailure(List<T> entries, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof ApiClientException) {
            logger.warning("Dropped " + entries.size() + " " + kind + " logs rejected by panel (HTTP "
                    + ((ApiClientException) cause).getStatusCode() + "): " + cause.getMessage());
            return;
        }
        rebuffer(entries, cause);
    }

    private void rebuffer(List<T> entries, Throwable cause) {
        if (cause instanceof PanelUnavailableException) {
            logger.warning("Failed to upload " + kind + " logs: Panel temporarily unavailable; re-buffering");
        } else {
            logger.warning("Failed to upload " + kind + " logs: " + cause.getMessage() + "; re-buffering");
        }
        synchronized (pending) {
            pending.addAll(0, entries);
            dropOldestOnOverflow();
        }
    }

    private void dropOldestOnOverflow() {
        int overflow = pending.size() - maxRebuffered;
        if (overflow > 0) {
            pending.subList(0, overflow).clear();
            logger.warning("Dropped " + overflow + " buffered " + kind + " logs (re-buffer cap " + maxRebuffered + " reached)");
        }
    }
}
