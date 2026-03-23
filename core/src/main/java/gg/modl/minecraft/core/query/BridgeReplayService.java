package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.UUID;
import java.util.concurrent.*;

public class BridgeReplayService implements ReplayService {
    private final ConcurrentHashMap<UUID, CompletableFuture<String>> pendingCaptures = new ConcurrentHashMap<>();
    private final QueryStatWipeExecutor executor;
    private final PluginLogger logger;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "modl-bridge-replay-timeout");
        t.setDaemon(true);
        return t;
    });

    public BridgeReplayService(QueryStatWipeExecutor executor, PluginLogger logger) {
        this.executor = executor;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingCaptures.put(targetUuid, future);

        executor.sendToAllBridges("CAPTURE_REPLAY", targetUuid.toString(), targetName);
        logger.info("[bridge] Sent CAPTURE_REPLAY for " + targetName + " (" + targetUuid + ")");

        scheduler.schedule(() -> {
            if (pendingCaptures.remove(targetUuid, future)) {
                future.complete(null);
                logger.info("[bridge] CAPTURE_REPLAY timed out for " + targetName);
            }
        }, 30, TimeUnit.SECONDS);

        return future;
    }

    @Override
    public boolean isReplayAvailable(UUID playerUuid) {
        return true; // can't check backend state without round-trip
    }

    public void handleCaptureResponse(UUID targetUuid, String replayId) {
        CompletableFuture<String> future = pendingCaptures.remove(targetUuid);
        if (future != null) {
            future.complete(replayId);
            logger.info("[bridge] Received CAPTURE_REPLAY_RESPONSE for " + targetUuid
                    + (replayId != null ? ": " + replayId : " (no replay)"));
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
