package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.core.service.FrozenPlayerStore;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@RequiredArgsConstructor
public class BridgeOnlyFreezeHandler implements Listener {
    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final BridgeScheduler scheduler;
    private final FrozenPlayerStore frozenPlayers;

    @Setter private StaffModeHandler staffModeHandler;

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!frozenPlayers.isFrozen(player.getUniqueId())) return;

        event.setCancelled(true);

        UUID playerUuid = player.getUniqueId();
        String message = localeManager.getMessage("freeze.chat",
                mapOf("player", player.getName(), "message", event.getMessage()));

        scheduler.runOnMainThread(() -> {
            if (staffModeHandler != null) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(online -> staffModeHandler.isInStaffMode(online.getUniqueId()))
                        .forEach(online -> sendScheduledMessage(online.getUniqueId(), message));
            }

            sendScheduledMessage(playerUuid, message);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!frozenPlayers.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(localeManager.getMessage("freeze.no_commands"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!frozenPlayers.releaseOnDisconnect(event.getPlayer().getUniqueId())) return;
        plugin.getLogger().warning("Frozen player " + event.getPlayer().getName() + " logged out!");
    }

    private void sendScheduledMessage(UUID target, String message) {
        scheduler.runForPlayer(target, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage(message);
            }
        });
    }
}
