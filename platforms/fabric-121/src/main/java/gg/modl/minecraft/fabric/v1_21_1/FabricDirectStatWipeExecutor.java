package gg.modl.minecraft.fabric.v1_21_1;

import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FabricDirectStatWipeExecutor implements StatWipeExecutor {
    private final FabricBridgeComponent bridgeComponent;
    private final String serverName;

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        StatWipeHandler handler = bridgeComponent.getStatWipeHandler();
        if (handler == null) return;
        boolean success = handler.execute(username, uuid, punishmentId);
        callback.onComplete(success, serverName);
    }
}
