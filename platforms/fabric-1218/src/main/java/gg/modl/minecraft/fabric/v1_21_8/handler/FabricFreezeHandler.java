package gg.modl.minecraft.fabric.v1_21_8.handler;

import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!freezeCore.isFrozen(uuid)) continue;

            FabricFreezeOps.FreezeAnchor anchor = ops.anchor(uuid);
            if (anchor == null) {
                ops.captureAnchor(uuid, player);
                continue;
            }

            double dx = player.getX() - anchor.getX();
            double dy = player.getY() - anchor.getY();
            double dz = player.getZ() - anchor.getZ();
            boolean wrongWorld = (ServerWorld) player.getWorld() != anchor.getWorld();

            if (wrongWorld || dx * dx + dy * dy + dz * dz > MAX_DRIFT_SQUARED) {
                player.teleport(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(),
                        Set.of(), anchor.getYaw(), anchor.getPitch(), false);
                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;
            }
        }
    }

    public void onPlayerQuit(UUID uuid) {
        freezeCore.handleQuit(uuid);
    }
}
