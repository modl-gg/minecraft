package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgePlayer;
import gg.modl.minecraft.bridge.BridgePlayerProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpigotPlayerProvider implements BridgePlayerProvider {

    @Override
    public BridgePlayer getPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? new SpigotBridgePlayer(player) : null;
    }

    @Override
    public Collection<BridgePlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .map(SpigotBridgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
