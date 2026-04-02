package gg.modl.minecraft.fabric.v1_21;

import gg.modl.minecraft.bridge.BridgePlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class FabricBridgePlayer implements BridgePlayer {
    private final ServerPlayerEntity player;

    public FabricBridgePlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    public ServerPlayerEntity getServerPlayer() {
        return player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUuid();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(Text.literal(message));
    }

    @Override
    public boolean isOnline() {
        return !player.isDisconnected();
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
        return player.getYaw();
    }

    @Override
    public float getPitch() {
        return player.getPitch();
    }

    @Override
    public String getWorldName() {
        return player.getServerWorld().getRegistryKey().getValue().toString();
    }
}
