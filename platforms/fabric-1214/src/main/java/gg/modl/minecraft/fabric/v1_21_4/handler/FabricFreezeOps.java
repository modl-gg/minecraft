package gg.modl.minecraft.fabric.v1_21_4.handler;

import gg.modl.minecraft.bridge.freeze.FreezeOps;
import lombok.Value;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

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
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        return frozenNames.get(uuid);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Text.literal(message));
        }
    }

    @Override
    public void onFrozen(UUID target) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(target);
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

    void captureAnchor(UUID uuid, ServerPlayerEntity player) {
        frozenNames.put(uuid, player.getName().getString());
        frozenAnchors.put(uuid, new FreezeAnchor((ServerWorld) player.getEntityWorld(),
                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
    }

    @Value
    static class FreezeAnchor {
        ServerWorld world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }
}
