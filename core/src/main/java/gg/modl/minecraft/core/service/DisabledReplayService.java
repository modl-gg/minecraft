package gg.modl.minecraft.core.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DisabledReplayService implements ReplayService {
    public static final DisabledReplayService FABRIC = new DisabledReplayService(ReplayCaptureStatus.FABRIC_DISABLED);

    private final ReplayCaptureStatus status;

    private DisabledReplayService(ReplayCaptureStatus status) {
        this.status = status;
    }

    @Override
    public CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName) {
        return CompletableFuture.completedFuture(ReplayCaptureResult.of(status, null));
    }

    @Override
    public ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
        return status;
    }
}
