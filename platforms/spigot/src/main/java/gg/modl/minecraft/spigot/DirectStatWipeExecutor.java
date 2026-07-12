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
            return;
        }

        dispatchStatWipeOnMainThread(handler, username, uuid, punishmentId, callback);
    }

    private void dispatchStatWipeOnMainThread(StatWipeHandler handler, String username, String uuid,
                                              String punishmentId, StatWipeCallback callback) {
        bridgeComponent.getScheduler().runOnMainThread(() -> {
            boolean success = handler.execute(username, uuid, punishmentId);
            callback.onComplete(success, serverName);
        });
    }
}
