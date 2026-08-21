package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.bridge.staffmode.PacketSidebar;
import gg.modl.minecraft.bridge.staffmode.ScoreboardContent;
import gg.modl.minecraft.bridge.staffmode.StaffGameMode;
import gg.modl.minecraft.bridge.staffmode.StaffModeOps;
import gg.modl.minecraft.spigot.bridge.folia.AsyncTeleporter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class SpigotStaffModeOps implements StaffModeOps {
    private static final String SCOREBOARD_OBJECTIVE_NAME = "modl_staff";
    private static final int TARGET_INVENTORY_SIZE = 54;

    private final Logger logger;
    private final AsyncTeleporter teleporter;
    private final PacketSidebar sidebar = new PacketSidebar(SCOREBOARD_OBJECTIVE_NAME);
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<String> warnedMaterials = ConcurrentHashMap.newKeySet();

    private volatile boolean pingMethodResolved;
    private volatile Method getPingMethod;

    SpigotStaffModeOps(Logger logger, AsyncTeleporter teleporter) {
        this.logger = logger;
        this.teleporter = teleporter;
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @Override
    public String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : null;
    }

    @Override
    public Set<UUID> onlinePlayerUuids() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
    }

    @Override
    public int onlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public int maxPlayerCount() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public int playerPing(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return -1;
        Method modern = resolveGetPingMethod();
        if (modern != null) {
            try {
                return (int) modern.invoke(player);
            } catch (ReflectiveOperationException e) {
                logger.log(Level.FINE, "getPing() invocation failed for " + player.getName(), e);
            }
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return handle.getClass().getField("ping").getInt(handle);
        } catch (ReflectiveOperationException e) {
            logger.log(Level.FINE, "NMS ping fallback failed for " + player.getName(), e);
            return -1;
        }
    }

    @Override
    public double playerHealth(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getHealth() : 0.0;
    }

    @Override
    public void clearInventory(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        player.getInventory().clear();
    }

    @Override
    public void clearArmor(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        player.getInventory().setArmorContents(new ItemStack[4]);
    }

    @Override
    public void setGameMode(UUID uuid, StaffGameMode mode) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        player.setGameMode(mode == StaffGameMode.CREATIVE ? GameMode.CREATIVE : GameMode.SURVIVAL);
    }

    @Override
    public void setHotbarSlot(UUID uuid, int slot, String materialId, String displayName, List<String> lore) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        ItemStack item = new ItemStack(parseMaterial(materialId));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slot, item);
    }

    @Override
    public boolean hasSnapshot(UUID uuid) {
        return snapshots.containsKey(uuid);
    }

    @Override
    public void saveSnapshot(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || snapshots.containsKey(uuid)) return;
        snapshots.put(uuid, new PlayerSnapshot(
                cloneItemArray(player.getInventory().getContents()),
                cloneItemArray(player.getInventory().getArmorContents()),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getExp(),
                player.getLevel()
        ));
    }

    @Override
    public boolean restoreSnapshot(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        PlayerSnapshot snapshot = snapshots.get(uuid);
        if (snapshot == null) return false;

        player.getInventory().setContents(snapshot.getInventoryContents());
        player.getInventory().setArmorContents(snapshot.getArmorContents());
        player.setGameMode(snapshot.getGameMode());
        player.setHealth(Math.min(snapshot.getHealth(), player.getMaxHealth()));
        player.setFoodLevel(snapshot.getFoodLevel());
        player.setExp(snapshot.getExp());
        player.setLevel(snapshot.getLevel());
        snapshots.remove(uuid);
        teleporter.teleport(player, snapshot.getLocation());
        return true;
    }

    @Override
    public Set<UUID> playersWithSnapshots() {
        return new HashSet<>(snapshots.keySet());
    }

    @Override
    public void clearSnapshots() {
        snapshots.clear();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void hidePlayer(UUID viewerUuid, UUID hiddenUuid) {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        Player hidden = Bukkit.getPlayer(hiddenUuid);
        if (viewer != null && viewer.isOnline() && hidden != null && hidden.isOnline()) {
            viewer.hidePlayer(hidden);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void showPlayer(UUID viewerUuid, UUID shownUuid) {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        Player shown = Bukkit.getPlayer(shownUuid);
        if (viewer != null && viewer.isOnline() && shown != null && shown.isOnline()) {
            viewer.showPlayer(shown);
        }
    }

    @Override
    public void teleportToPlayer(UUID uuid, UUID targetUuid) {
        Player player = Bukkit.getPlayer(uuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (player != null && target != null) {
            teleporter.teleport(player, target.getLocation());
        }
    }

    @Override
    public void createScoreboard(UUID uuid, ScoreboardContent content) {
        sidebar.show(uuid, Bukkit.getPlayer(uuid), content);
    }

    @Override
    public void updateScoreboard(UUID uuid, ScoreboardContent content) {
        sidebar.update(uuid, Bukkit.getPlayer(uuid), content);
    }

    @Override
    public void removeScoreboard(UUID uuid) {
        sidebar.hide(uuid, Bukkit.getPlayer(uuid));
    }

    @Override
    public void discardScoreboard(UUID uuid) {
        sidebar.forget(uuid);
    }

    @Override
    public void clearScoreboards() {
        sidebar.forgetAll();
    }

    @Override
    public void openTargetInventory(UUID viewerUuid, UUID targetUuid) {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (viewer == null || target == null) return;
        Inventory copy = Bukkit.createInventory(null, TARGET_INVENTORY_SIZE,
                StaffModeHandler.SILENT_CONTAINER_PREFIX + target.getName() + "'s Inventory");
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < contents.length && i < TARGET_INVENTORY_SIZE; i++) {
            copy.setItem(i, contents[i] == null ? null : contents[i].clone());
        }
        viewer.openInventory(copy);
    }

    @Override
    public void runPlayerCommand(UUID uuid, String command) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.performCommand(command);
        }
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.replace("minecraft:", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            if (warnedMaterials.add(name)) {
                logger.warning("[staff-mode] Unknown hotbar material '" + name + "', using STONE");
            }
            return Material.STONE;
        }
    }

    private static ItemStack[] cloneItemArray(ItemStack[] source) {
        return Arrays.stream(source)
                .map(item -> item != null ? item.clone() : null)
                .toArray(ItemStack[]::new);
    }

    private Method resolveGetPingMethod() {
        if (!pingMethodResolved) {
            synchronized (this) {
                if (!pingMethodResolved) {
                    try {
                        getPingMethod = Player.class.getMethod("getPing");
                    } catch (NoSuchMethodException e) {
                        getPingMethod = null;
                    }
                    pingMethodResolved = true;
                }
            }
        }
        return getPingMethod;
    }
}
