package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BridgeReplayService implements ReplayService {
    private static final long CAPTURE_TIMEOUT_SECONDS = 600;

    private final ConcurrentHashMap<UUID, PendingCapture> pendingCaptures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReplayCaptureStatus> targetStatuses = new ConcurrentHashMap<>();
    private final BridgeBroadcaster broadcaster;
    private final PluginLogger logger;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final Runnable beforeDispatchHook;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "modl-bridge-replay-timeout");
        t.setDaemon(true);
        return t;
    });

    public BridgeReplayService(BridgeBroadcaster broadcaster, PluginLogger logger) {
        this(broadcaster, logger, () -> {});
    }

    BridgeReplayService(BridgeBroadcaster broadcaster, PluginLogger logger, Runnable beforeDispatchHook) {
        this.broadcaster = broadcaster;
        this.logger = logger;
        this.beforeDispatchHook = beforeDispatchHook;
    }

    @Override
    public CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(ReplayCaptureResult.error());
        }

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
        beforeDispatchHook.run();
        int dispatched = dispatchIfOpen(targetUuid, targetName, capture);
        if (dispatched < 0) {
            return capture.future;
        }
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

        try {
            scheduler.schedule(() -> {
                if (pendingCaptures.remove(targetUuid, capture)) {
                    capture.future.complete(ReplayCaptureResult.error());
                    logger.info("[bridge] CAPTURE_REPLAY timed out for " + targetName);
                }
            }, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            if (pendingCaptures.remove(targetUuid, capture)) {
                capture.future.complete(ReplayCaptureResult.error());
            }
        }

        return capture.future;
    }

    @Override
    public ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
        if (!broadcaster.hasConnectedClients()) {
            return ReplayCaptureStatus.NO_BRIDGE_CONNECTED;
        }

        ReplayCaptureStatus status = targetStatuses.get(playerUuid);
        return status != null ? status : ReplayCaptureStatus.NO_ACTIVE_RECORDING;
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
            targetStatuses.put(targetUuid, ReplayCaptureStatus.OK);
            if (pendingCaptures.remove(targetUuid, capture)) {
                capture.future.complete(ReplayCaptureResult.ok(replayId));
                logger.info("[bridge] Received CAPTURE_REPLAY_RESPONSE for " + targetUuid + ": " + replayId);
            }
            return;
        }

        ReplayCaptureResult finalResult = capture.recordResponse(effectiveStatus);
        ReplayCaptureStatus currentStatus = capture.getCurrentStatus();
        if (currentStatus != null) {
            targetStatuses.put(targetUuid, currentStatus);
        }
        if (finalResult != null && pendingCaptures.remove(targetUuid, capture)) {
            capture.future.complete(finalResult);
            logger.info("[bridge] Received CAPTURE_REPLAY_RESPONSE for " + targetUuid
                    + " (" + finalResult.getStatus() + ")");
        }
    }

    public void removeTargetStatus(UUID targetUuid) {
        targetStatuses.remove(targetUuid);
    }

    @Override
    public void onPlayerDisconnect(UUID playerUuid) {
        removeTargetStatus(playerUuid);
    }

    public void shutdown() {
        lifecycleLock.writeLock().lock();
        try {
            closed.set(true);
            pendingCaptures.forEach((targetUuid, capture) -> {
                if (pendingCaptures.remove(targetUuid, capture)) {
                    capture.future.complete(ReplayCaptureResult.error());
                }
            });
        } finally {
            lifecycleLock.writeLock().unlock();
        }
        targetStatuses.clear();
        scheduler.shutdownNow();
    }

    boolean isShutdown() {
        return scheduler.isShutdown();
    }

    private static ReplayCaptureStatus normalizeStatus(String replayId, ReplayCaptureStatus status) {
        if (replayId != null && !replayId.isEmpty()) {
            return ReplayCaptureStatus.OK;
        }
        return status != null ? status : ReplayCaptureStatus.NOT_LOCAL;
    }

    private int dispatchIfOpen(UUID targetUuid, String targetName, PendingCapture capture) {
        lifecycleLock.readLock().lock();
        try {
            if (closed.get()) {
                pendingCaptures.remove(targetUuid, capture);
                capture.future.complete(ReplayCaptureResult.error());
                return -1;
            }
            return broadcaster.sendToAllBridges(BridgeAction.CAPTURE_REPLAY.wire(), targetUuid.toString(), targetName);
        } finally {
            lifecycleLock.readLock().unlock();
        }
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

        private synchronized ReplayCaptureStatus getCurrentStatus() {
            if (error) return ReplayCaptureStatus.ERROR;
            if (fabricDisabled) return ReplayCaptureStatus.FABRIC_DISABLED;
            if (noActiveRecording) return ReplayCaptureStatus.NO_ACTIVE_RECORDING;
            if (notLocal) return ReplayCaptureStatus.NOT_LOCAL;
            return null;
        }

        private ReplayCaptureResult getFinalResultIfReady() {
            if (expectedResponses <= 0 || receivedResponses < expectedResponses) return null;

            if (error) return ReplayCaptureResult.error();
            if (fabricDisabled) return ReplayCaptureResult.fabricDisabled();
            return ReplayCaptureResult.noActiveRecording();
        }
    }
}
