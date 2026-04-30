package gg.modl.minecraft.core.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReplayService {
    CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName);

    default CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
        return captureReplayResult(targetUuid, targetName).thenApply(result ->
                result.getStatus() == ReplayCaptureStatus.OK ? result.getReplayId() : null);
    }

    default ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
        return ReplayCaptureStatus.NO_ACTIVE_RECORDING;
    }

    default boolean isReplayAvailable(UUID playerUuid) {
        return getReplayStatus(playerUuid) == ReplayCaptureStatus.OK;
    }
}
