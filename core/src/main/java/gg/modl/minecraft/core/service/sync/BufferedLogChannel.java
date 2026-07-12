package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

final class BufferedLogChannel<T> {
    private final String kind;
    private final Supplier<List<T>> drainSource;
    private final Function<T, String> usernameAccessor;
    private final Function<List<T>, CompletableFuture<Void>> submit;
    private final PluginLogger logger;
    private final int maxRebuffered;
    private final List<T> pending = new ArrayList<>();

    BufferedLogChannel(String kind, Supplier<List<T>> drainSource, Function<T, String> usernameAccessor,
                       Function<List<T>, CompletableFuture<Void>> submit, PluginLogger logger, int maxRebuffered) {
        this.kind = kind;
        this.drainSource = drainSource;
        this.usernameAccessor = usernameAccessor;
        this.submit = submit;
        this.logger = logger;
        this.maxRebuffered = maxRebuffered;
    }

    CompletableFuture<Void> flush() {
        List<T> entries = SyncService.filterByUsername(drain(), usernameAccessor);
        if (entries.isEmpty()) return null;
        return submit.apply(entries).exceptionally(throwable -> {
            rebuffer(entries, throwable);
            return null;
        });
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

    private void rebuffer(List<T> entries, Throwable throwable) {
        warnFlushFailure(throwable);
        synchronized (pending) {
            pending.addAll(0, entries);
            dropOldestOnOverflow();
        }
    }

    private void warnFlushFailure(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            logger.warning("Failed to upload " + kind + " logs: Panel temporarily unavailable; re-buffering");
        } else {
            logger.warning("Failed to upload " + kind + " logs: " + cause.getMessage() + "; re-buffering");
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
