package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.freeze.FreezeOps;
import lombok.Value;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class FabricFreezeOps implements FreezeOps {
    private final MinecraftServer server;
    private final Map<UUID, String> frozenNames = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeAnchor> frozenAnchors = new ConcurrentHashMap<>();

    FabricFreezeOps(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String playerName(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        return frozenNames.get(uuid);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void onFrozen(UUID target) {
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) captureAnchor(target, player);
    }

    @Override
    public void onUnfrozen(UUID target) {
        frozenAnchors.remove(target);
        frozenNames.remove(target);
    }

    FreezeAnchor anchor(UUID uuid) {
        return frozenAnchors.get(uuid);
    }

    void captureAnchor(UUID uuid, ServerPlayer player) {
        frozenNames.put(uuid, player.getName().getString());
        frozenAnchors.put(uuid, new FreezeAnchor((ServerLevel) player.level(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }

    @Value
    static class FreezeAnchor {
        ServerLevel world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }
}
