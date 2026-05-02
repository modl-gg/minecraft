package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class FreezeHandler implements Listener {
    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final BridgeScheduler scheduler;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>(); // frozen -> staff

    @Setter private StaffModeHandler staffModeHandler;
    @Setter private BridgeQueryClient bridgeClient;

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void freeze(String targetUuid, String staffUuid) {
        frozenPlayers.put(UUID.fromString(targetUuid), UUID.fromString(staffUuid));
        notifyPlayer(UUID.fromString(targetUuid), "freeze.frozen");
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);
        notifyPlayer(target, "freeze.unfrozen");
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player.getUniqueId())) return;

        event.setCancelled(true);

        String message = localeManager.getMessage("freeze.chat",
                mapOf("player", player.getName(), "message", event.getMessage()));

        if (staffModeHandler != null) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(online -> staffModeHandler.isInStaffMode(online.getUniqueId()))
                    .forEach(online -> online.sendMessage(message));
        }

        player.sendMessage(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (cancelIfFrozen(event, event.getPlayer())) {
            event.getPlayer().sendMessage(localeManager.getMessage("freeze.no_commands"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (frozenPlayers.remove(uuid) == null) return;

        String playerName = event.getPlayer().getName();
        plugin.getLogger().warning("Frozen player " + playerName + " logged out!");

        if (bridgeClient != null) {
            bridgeClient.sendMessage("FREEZE_LOGOUT", uuid.toString(), playerName);
        }
    }

    private boolean cancelIfFrozen(Cancellable event, Player player) {
        if (!isFrozen(player.getUniqueId())) return false;
        event.setCancelled(true);
        return true;
    }

    private void notifyPlayer(UUID target, String messageKey) {
        scheduler.runForPlayer(target, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage(localeManager.getMessage(messageKey));
            }
        });
    }
}
