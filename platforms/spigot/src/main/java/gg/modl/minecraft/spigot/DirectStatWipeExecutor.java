package gg.modl.minecraft.spigot;

import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import gg.modl.minecraft.spigot.bridge.BridgeComponent;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectStatWipeExecutor implements StatWipeExecutor {
    private final BridgeComponent bridgeComponent;
    private final String serverName;

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        final StatWipeHandler handler = bridgeComponent.getStatWipeHandler();
        if (handler == null) {
            return; // will retry on next sync
        }

        // Stat-wipe dispatches console commands; hop to the main/server thread so they
        // never run from the async login/sync/realtime threads (runSync is inline when
        // already on the primary thread, and Folia-correct).
        bridgeComponent.getScheduler().runSync(() -> {
            boolean success = handler.execute(username, uuid, punishmentId);
            callback.onComplete(success, serverName);
        });
    }
}
