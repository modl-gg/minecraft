package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.UUID;
import java.util.concurrent.*;

public class BridgeReplayService implements ReplayService {
    private static final long CAPTURE_TIMEOUT_SECONDS = 600;

    private final ConcurrentHashMap<UUID, PendingCapture> pendingCaptures = new ConcurrentHashMap<>();
    private final BridgeBroadcaster broadcaster;
    private final PluginLogger logger;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "modl-bridge-replay-timeout");
        t.setDaemon(true);
        return t;
    });

    public BridgeReplayService(BridgeBroadcaster broadcaster, PluginLogger logger) {
        this.broadcaster = broadcaster;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
        return captureReplayResult(targetUuid, targetName).thenApply(result ->
                result.getStatus() == ReplayCaptureStatus.OK ? result.getReplayId() : null);
    }

    @Override
    public CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName) {
        PendingCapture existingCapture = pendingCaptures.get(targetUuid);
        if (existingCapture != null) {
            logger.info("[bridge] Replay capture already pending for " + targetName + " (" + targetUuid + ")");
            return existingCapture.future;
        }

        if (!broadcaster.hasConnectedClients()) {
            logger.warning("[bridge] No connected backends for replay capture of " + targetName);
            return CompletableFuture.completedFuture(ReplayCaptureResult.noBridgeConnected());
        }

        PendingCapture capture = new PendingCapture();
        PendingCapture racedCapture = pendingCaptures.putIfAbsent(targetUuid, capture);
        if (racedCapture != null) {
            logger.info("[bridge] Replay capture already pending for " + targetName + " (" + targetUuid + ")");
            return racedCapture.future;
        }

        int dispatched = broadcaster.sendToAllBridges("CAPTURE_REPLAY", targetUuid.toString(), targetName);
        if (capture.future.isDone()) {
            return capture.future;
        }
        if (dispatched <= 0) {
            pendingCaptures.remove(targetUuid, capture);
            capture.future.complete(ReplayCaptureResult.noBridgeConnected());
            logger.warning("[bridge] Failed to dispatch CAPTURE_REPLAY for " + targetName);
            return capture.future;
        }
        ReplayCaptureResult earlyResult = capture.setExpectedResponses(dispatched);
        if (earlyResult != null && pendingCaptures.remove(targetUuid, capture)) {
            capture.future.complete(earlyResult);
        }
        logger.info("[bridge] Dispatched CAPTURE_REPLAY for " + targetName + " (" + targetUuid + ")");

        scheduler.schedule(() -> {
            if (pendingCaptures.remove(targetUuid, capture)) {
                capture.future.complete(ReplayCaptureResult.error());
                logger.info("[bridge] CAPTURE_REPLAY timed out for " + targetName);
            }
        }, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return capture.future;
    }

    @Override
    public boolean isReplayAvailable(UUID playerUuid) {
        return getReplayStatus(playerUuid) == ReplayCaptureStatus.OK;
    }

    @Override
    public ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
        return broadcaster.hasConnectedClients() ? ReplayCaptureStatus.OK : ReplayCaptureStatus.NO_BRIDGE_CONNECTED;
    }

    public void handleCaptureResponse(UUID targetUuid, String replayId) {
        ReplayCaptureStatus status = replayId != null && !replayId.isEmpty()
                ? ReplayCaptureStatus.OK
                : ReplayCaptureStatus.NOT_LOCAL;
        handleCaptureResponse(targetUuid, replayId, status);
    }

    public void handleCaptureResponse(UUID targetUuid, String replayId, ReplayCaptureStatus status) {
        PendingCapture capture = pendingCaptures.get(targetUuid);
        if (capture == null) {
            return;
        }

        ReplayCaptureStatus effectiveStatus = normalizeStatus(replayId, status);
        if (effectiveStatus == ReplayCaptureStatus.OK) {
            if (pendingCaptures.remove(targetUuid, capture)) {
                capture.future.complete(ReplayCaptureResult.ok(replayId));
                logger.info("[bridge] Received CAPTURE_REPLAY_RESPONSE for " + targetUuid + ": " + replayId);
            }
            return;
        }

        ReplayCaptureResult finalResult = capture.recordResponse(effectiveStatus);
        if (finalResult != null && pendingCaptures.remove(targetUuid, capture)) {
            capture.future.complete(finalResult);
            logger.info("[bridge] Received CAPTURE_REPLAY_RESPONSE for " + targetUuid
                    + " (" + finalResult.getStatus() + ")");
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private static ReplayCaptureStatus normalizeStatus(String replayId, ReplayCaptureStatus status) {
        if (replayId != null && !replayId.isEmpty()) {
            return ReplayCaptureStatus.OK;
        }
        return status != null ? status : ReplayCaptureStatus.NOT_LOCAL;
    }

    private static final class PendingCapture {
        private final CompletableFuture<ReplayCaptureResult> future = new CompletableFuture<>();
        private int expectedResponses;
        private int receivedResponses;
        private boolean fabricDisabled;
        private boolean noActiveRecording;
        private boolean notLocal;
        private boolean error;

        private synchronized ReplayCaptureResult setExpectedResponses(int expectedResponses) {
            this.expectedResponses = expectedResponses;
            return getFinalResultIfReady();
        }

        private synchronized ReplayCaptureResult recordResponse(ReplayCaptureStatus status) {
            receivedResponses++;
            if (status == ReplayCaptureStatus.FABRIC_DISABLED) {
                fabricDisabled = true;
            } else if (status == ReplayCaptureStatus.NO_ACTIVE_RECORDING) {
                noActiveRecording = true;
            } else if (status == ReplayCaptureStatus.NOT_LOCAL) {
                notLocal = true;
            } else if (status == ReplayCaptureStatus.ERROR) {
                error = true;
            }

            return getFinalResultIfReady();
        }

        private ReplayCaptureResult getFinalResultIfReady() {
            if (expectedResponses <= 0 || receivedResponses < expectedResponses) return null;

            if (fabricDisabled) return ReplayCaptureResult.fabricDisabled();
            if (error) return ReplayCaptureResult.error();
            if (noActiveRecording || notLocal) return ReplayCaptureResult.noActiveRecording();
            return ReplayCaptureResult.noActiveRecording();
        }
    }
}
