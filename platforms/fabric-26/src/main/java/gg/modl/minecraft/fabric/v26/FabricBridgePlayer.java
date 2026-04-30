package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.BridgePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FabricBridgePlayer implements BridgePlayer {
    private final ServerPlayer player;

    public FabricBridgePlayer(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getServerPlayer() {
        return player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean isOnline() {
        return !player.hasDisconnected();
    }

    @Override
    public int getEntityId() {
        return player.getId();
    }

    @Override
    public double getX() {
        return player.getX();
    }

    @Override
    public double getY() {
        return player.getY();
    }

    @Override
    public double getZ() {
        return player.getZ();
    }

    @Override
    public float getYaw() {
        return player.getYRot();
    }

    @Override
    public float getPitch() {
        return player.getXRot();
    }

    @Override
    public String getWorldName() {
        return player.level().dimension().identifier().toString();
    }
}
