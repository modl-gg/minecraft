package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

import java.util.UUID;

public class FreezeHandler implements Listener {
    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final BridgeScheduler scheduler;
    private final FreezeCore freezeCore;

    @Setter private StaffModeHandler staffModeHandler;

    public FreezeHandler(JavaPlugin plugin, BridgeLocaleManager localeManager, BridgeScheduler scheduler) {
        this.plugin = plugin;
        this.localeManager = localeManager;
        this.scheduler = scheduler;
        this.freezeCore = new FreezeCore(localeManager, new SpigotFreezeOps(scheduler));
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        freezeCore.setBridgeClient(bridgeClient);
    }

    public void freeze(String targetUuid, String staffUuid) {
        freezeCore.freeze(targetUuid, staffUuid);
    }

    public void unfreeze(String targetUuid) {
        freezeCore.unfreeze(targetUuid);
    }

    public boolean isFrozen(UUID uuid) {
        return freezeCore.isFrozen(uuid);
    }

    FreezeCore getFreezeCore() {
        return freezeCore;
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
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            cancelIfFrozen(event, (Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
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
        if (!isFrozen(uuid)) return;

        plugin.getLogger().warning("Frozen player " + event.getPlayer().getName() + " logged out!");
        freezeCore.handleQuit(uuid);
    }

    private boolean cancelIfFrozen(Cancellable event, Player player) {
        if (!isFrozen(player.getUniqueId())) return false;
        event.setCancelled(true);
        return true;
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
