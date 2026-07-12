package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public class FabricFreezeHandler {
    private static final double MAX_DRIFT_SQUARED = 0.01;

    private final MinecraftServer server;
    private final FabricFreezeOps ops;
    private final FreezeCore freezeCore;

    public FabricFreezeHandler(MinecraftServer server, BridgeLocaleManager localeManager) {
        this.server = server;
        this.ops = new FabricFreezeOps(server);
        this.freezeCore = new FreezeCore(localeManager, ops);
    }

    FreezeCore getFreezeCore() {
        return freezeCore;
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        freezeCore.setBridgeClient(bridgeClient);
    }

    public void freeze(String targetUuid, String staffUuid) {
        freezeCore.freeze(targetUuid, staffUuid);
    }

    public void unfreeze(String targetUuid) {
        freezeCore.unfreeze(targetUuid);
    }

    public boolean isFrozen(UUID uuid) {
        return freezeCore.isFrozen(uuid);
    }

    public void onTick() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!freezeCore.isFrozen(uuid)) continue;

            FabricFreezeOps.FreezeAnchor anchor = ops.anchor(uuid);
            if (anchor == null) {
                ops.captureAnchor(uuid, player);
                continue;
            }

            double dx = player.getX() - anchor.x;
            double dy = player.getY() - anchor.y;
            double dz = player.getZ() - anchor.z;
            boolean wrongWorld = (ServerLevel) player.level() != anchor.world;

            if (wrongWorld || dx * dx + dy * dy + dz * dz > MAX_DRIFT_SQUARED) {
                player.teleportTo(anchor.world, anchor.x, anchor.y, anchor.z,
                        Set.<Relative>of(), anchor.yaw, anchor.pitch, false);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
        }
    }

    public void onPlayerQuit(UUID uuid) {
        freezeCore.handleQuit(uuid);
    }
}
