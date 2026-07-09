package gg.modl.minecraft.fabric.v1_21_4;

import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FabricDirectStatWipeExecutor implements StatWipeExecutor {
    private final FabricBridgeComponent bridgeComponent;
    private final String serverName;

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        final StatWipeHandler handler = bridgeComponent.getStatWipeHandler();
        if (handler == null) return;
        // Stat-wipe dispatches console commands; hop to the server thread so they never
        // run from the async login/sync/realtime threads.
        bridgeComponent.getServer().execute(() -> {
            boolean success = handler.execute(username, uuid, punishmentId);
            callback.onComplete(success, serverName);
        });
    }
}
