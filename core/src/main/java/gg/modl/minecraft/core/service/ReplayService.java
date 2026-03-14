package gg.modl.minecraft.core.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for capturing and uploading replays.
 * Implemented via reflection bridge to ModlBridge plugin's BridgeReplayService.
 */
public interface ReplayService {

    /**
     * Captures the current replay buffer for the target player, uploads it,
     * and returns the viewer URL.
     *
     * @return future resolving to the viewer URL, or null if capture/upload failed
     */
    CompletableFuture<String> captureReplay(UUID targetUuid, String targetName);

    /**
     * Checks if replay recording is available and active for the given player.
     */
    boolean isReplayAvailable(UUID playerUuid);
}
