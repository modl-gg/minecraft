package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.freeze.FreezeOps;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@RequiredArgsConstructor
class SpigotFreezeOps implements FreezeOps {
    private final BridgeScheduler scheduler;

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
    }

    @Override
    public void onUnfrozen(UUID target) {
    }
}
