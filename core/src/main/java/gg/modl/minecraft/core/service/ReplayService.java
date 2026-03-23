package gg.modl.minecraft.core.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReplayService {
    CompletableFuture<String> captureReplay(UUID targetUuid, String targetName);
    boolean isReplayAvailable(UUID playerUuid);
}
