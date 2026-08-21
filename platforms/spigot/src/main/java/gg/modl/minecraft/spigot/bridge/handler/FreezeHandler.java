package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.service.FrozenPlayerStore;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.spigot.bridge.folia.AsyncTeleporter;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class FreezeHandler implements Listener {
    private final JavaPlugin plugin;
    private final SpigotFreezeOps ops;
    private final FreezeCore freezeCore;

    public FreezeHandler(JavaPlugin plugin, BridgeLocaleManager localeManager, PluginLogger pluginLogger,
                         BridgeScheduler scheduler) {
        this.plugin = plugin;
        this.ops = new SpigotFreezeOps(scheduler, new AsyncTeleporter(pluginLogger));
        this.freezeCore = new FreezeCore(localeManager, ops);
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

    public FrozenPlayerStore getFrozenPlayerStore() {
        return freezeCore;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!isFrozen(uuid)) return;

        Location to = event.getTo();
        if (to == null) return;

        FreezeAnchor anchor = ops.anchor(uuid);
        if (anchor == null) {
            ops.captureAnchor(uuid, event.getFrom());
            return;
        }

        if (!anchor.inWorldOf(to)) {
            ops.returnToAnchor(uuid);
            return;
        }

        if (!anchor.holds(to)) {
            event.setTo(anchor.toLocation(to.getWorld(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!isFrozen(uuid)) return;

        FreezeAnchor anchor = ops.anchor(uuid);
        Location to = event.getTo();
        if (anchor == null || to == null || !anchor.holds(to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!isFrozen(uuid)) return;
        ops.captureAnchor(uuid, event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player) {
            cancelIfFrozen(event, (Player) event.getEntered());
        }
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
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfFrozen(event, event.getPlayer());
    }

    private boolean cancelIfFrozen(Cancellable event, Player player) {
        if (!isFrozen(player.getUniqueId())) return false;
        event.setCancelled(true);
        return true;
    }
}
