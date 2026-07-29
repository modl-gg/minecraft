package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.staffmode.StaffModeCore;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class StaffModeHandler implements Listener {
    static final String SILENT_CONTAINER_PREFIX = "§8Viewing: ";

    private final JavaPlugin plugin;
    private final StaffModeCore core;

    public StaffModeHandler(JavaPlugin plugin, BridgeConfig bridgeConfig, FreezeHandler freezeHandler,
                            BridgeLocaleManager localeManager, StaffModeConfig staffModeConfig, BridgeScheduler scheduler) {
        this.plugin = plugin;
        this.core = new StaffModeCore(bridgeConfig, staffModeConfig, localeManager, scheduler,
                freezeHandler.getFreezeCore(), new SpigotStaffModeOps(plugin.getLogger()));
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        core.start();
    }

    public void shutdown() {
        core.shutdown();
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        core.setBridgeClient(bridgeClient);
    }

    public boolean isInStaffMode(UUID uuid) {
        return core.isInStaffMode(uuid);
    }

    public void enterStaffMode(String staffUuid) {
        core.enterStaffMode(staffUuid);
    }

    public void exitStaffMode(String staffUuid) {
        core.exitStaffMode(staffUuid);
    }

    public void setTarget(String staffUuid, String targetUuid) {
        core.setTarget(staffUuid, targetUuid);
    }

    public void vanishFromBridge(String staffUuid) {
        core.vanishFromBridge(staffUuid);
    }

    public void unvanishFromBridge(String staffUuid) {
        core.unvanishFromBridge(staffUuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.getAction() == Action.PHYSICAL) {
            if (core.isVanished(uuid)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!core.isInStaffMode(uuid)) return;

        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta()) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                core.handleHotbarAction(uuid, player.getInventory().getHeldItemSlot());
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && core.isVanished(uuid)) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof InventoryHolder) {
                InventoryHolder holder = (InventoryHolder) block.getState();
                event.setCancelled(true);
                Inventory copy = Bukkit.createInventory(null,
                        holder.getInventory().getSize(),
                        SILENT_CONTAINER_PREFIX + block.getType().name());
                copy.setContents(holder.getInventory().getContents());
                player.openInventory(copy);
                return;
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!core.isInStaffMode(uuid)) return;
        event.setCancelled(true);

        if (event.getRightClicked() instanceof Player) {
            Player clicked = (Player) event.getRightClicked();
            core.handleTargetSelect(uuid, player.getInventory().getHeldItemSlot(), clicked.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelIfInStaffMode(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelIfInStaffMode(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (core.isInStaffMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageOther(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (core.isInStaffMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(PlayerPickupItemEvent event) {
        if (core.isInStaffMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfInStaffMode(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (core.isInStaffMode(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!core.isInStaffMode(player.getUniqueId())) return;

        if (event.getView().getTitle().startsWith(SILENT_CONTAINER_PREFIX)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (core.isVanished(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        core.handlePlayerJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        core.handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    private void cancelIfInStaffMode(Player player, Cancellable event) {
        if (core.isInStaffMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
