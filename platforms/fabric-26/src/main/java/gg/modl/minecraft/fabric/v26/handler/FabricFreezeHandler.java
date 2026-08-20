package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.freeze.FreezeDrift;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.service.FrozenPlayerStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public class FabricFreezeHandler {

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

    public FrozenPlayerStore getFrozenPlayerStore() {
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

            double dx = player.getX() - anchor.getX();
            double dy = player.getY() - anchor.getY();
            double dz = player.getZ() - anchor.getZ();
            boolean wrongWorld = (ServerLevel) player.level() != anchor.getWorld();

            if (wrongWorld || FreezeDrift.exceeded(dx, dy, dz)) {
                player.teleportTo(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(),
                        Set.<Relative>of(), anchor.getYaw(), anchor.getPitch(), false);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
        }
    }

    public void onPlayerQuit(UUID uuid) {
        freezeCore.releaseOnDisconnect(uuid);
    }
}
