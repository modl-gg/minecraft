package gg.modl.minecraft.spigot;

import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import gg.modl.minecraft.spigot.bridge.BridgeComponent;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;

/**
 * Direct stat wipe executor that calls the bridge's StatWipeHandler directly,
 * replacing the reflection-based SpigotStatWipeExecutor.
 */
@RequiredArgsConstructor
public class DirectStatWipeExecutor implements StatWipeExecutor {
    private final BridgeComponent bridgeComponent;
    private final String serverName;

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        StatWipeHandler handler = bridgeComponent.getStatWipeHandler();
        if (handler == null) {
            return; // will retry on next sync
        }

        boolean success = handler.execute(username, uuid, punishmentId);
        callback.onComplete(success, serverName);
    }
}
