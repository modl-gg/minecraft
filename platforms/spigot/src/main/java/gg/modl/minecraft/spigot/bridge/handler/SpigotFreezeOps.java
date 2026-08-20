package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.freeze.FreezeOps;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
class SpigotFreezeOps implements FreezeOps {
    private final BridgeScheduler scheduler;
    private final Map<UUID, FreezeAnchor> anchors = new ConcurrentHashMap<>();

    @Override
    public String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        scheduler.runForPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        });
    }

    @Override
    public void onFrozen(UUID target) {
        scheduler.runForPlayer(target, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player == null) return;
            player.leaveVehicle();
            player.setVelocity(new Vector(0, 0, 0));
            captureAnchor(target, player.getLocation());
        });
    }

    @Override
    public void onUnfrozen(UUID target) {
        anchors.remove(target);
    }

    FreezeAnchor anchor(UUID uuid) {
        return anchors.get(uuid);
    }

    void captureAnchor(UUID uuid, Location location) {
        if (location.getWorld() == null) return;
        anchors.put(uuid, FreezeAnchor.of(location));
    }

    void returnToAnchor(UUID uuid) {
        FreezeAnchor anchor = anchors.get(uuid);
        if (anchor == null) return;

        World world = Bukkit.getWorld(anchor.getWorldId());
        if (world == null) return;

        scheduler.runForPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) return;
            player.teleport(anchor.toLocation(world, player.getLocation().getYaw(), player.getLocation().getPitch()));
            player.setVelocity(new Vector(0, 0, 0));
        });
    }
}
