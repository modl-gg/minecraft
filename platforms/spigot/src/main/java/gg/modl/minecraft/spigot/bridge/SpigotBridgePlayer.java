package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgePlayer;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

@RequiredArgsConstructor
public class SpigotBridgePlayer implements BridgePlayer {
    private final Player player;

    public Player getBukkitPlayer() {
        return player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public int getEntityId() {
        return player.getEntityId();
    }

    @Override
    public double getX() {
        return player.getLocation().getX();
    }

    @Override
    public double getY() {
        return player.getLocation().getY();
    }

    @Override
    public double getZ() {
        return player.getLocation().getZ();
    }

    @Override
    public float getYaw() {
        return player.getLocation().getYaw();
    }

    @Override
    public float getPitch() {
        return player.getLocation().getPitch();
    }

    @Override
    public String getWorldName() {
        return player.getWorld().getName();
    }
}
