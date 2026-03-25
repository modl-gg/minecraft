package gg.modl.minecraft.spigot.bridge.handler;

import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import gg.modl.minecraft.spigot.bridge.config.StaffModeConfig;
import gg.modl.minecraft.spigot.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.spigot.bridge.query.BridgeQueryClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import static gg.modl.minecraft.core.util.Java8Collections.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class StaffModeHandler implements Listener {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String SCOREBOARD_OBJECTIVE_NAME = "staffmode";
    private static final String SILENT_CONTAINER_PREFIX = "\u00a78Viewing: ";
    private static final int HOTBAR_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final int SCOREBOARD_MAX_LINE_LENGTH = 40;
    private static final String ACTION_TARGET_SELECTOR = "target_selector";
    private static final String ACTION_VANISH_TOGGLE = "vanish_toggle";
    private static final String ACTION_STAFF_MENU = "staff_menu";
    private static final String ACTION_RANDOM_TELEPORT = "random_teleport";
    private static final String ACTION_FREEZE_TARGET = "freeze_target";
    private static final String ACTION_STOP_TARGET = "stop_target";
    private static final String ACTION_INSPECT_TARGET = "inspect_target";
    private static final String ACTION_OPEN_INVENTORY = "open_inventory";
    private static final String ACTION_TELEPORT_TO_TARGET = "teleport_to_target";
    private static final String ACTION_HACKREPORT_TARGET = "hackreport_target";
    private static final String PLACEHOLDER_NA = "N/A";

    private final JavaPlugin plugin;
    private final BridgeConfig bridgeConfig;
    private final FreezeHandler freezeHandler;
    private final BridgeLocaleManager localeManager;
    private final StaffModeConfig staffModeConfig;
    @Setter private BridgeQueryClient bridgeClient;

    private final Set<UUID> staffModeActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> targetMap = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new ConcurrentHashMap<>();
    private ScheduledExecutorService scoreboardExecutor;

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startScoreboardUpdater();
    }

    public void shutdown() {
        if (scoreboardExecutor != null) {
            scoreboardExecutor.shutdownNow();
        }
        staffModeActive.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .forEach(p -> {
                    restoreSnapshot(p);
                    removeScoreboard(p);
                });
        activeScoreboards.clear();
        staffModeActive.clear();
    }

    private void startScoreboardUpdater() {
        scoreboardExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-bridge-scoreboard");
            t.setDaemon(true);
            return t;
        });
        scoreboardExecutor.scheduleAtFixedRate(() -> Bukkit.getScheduler().runTask(plugin, this::updateAllScoreboards), 1, 1, TimeUnit.SECONDS);
    }

    private void createScoreboard(Player player) {
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUniqueId());
        if (!config.isEnabled()) return;

        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = replacePlaceholders(config.getTitle(), player);
        @SuppressWarnings("deprecation")
        Objective obj = sb.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, "dummy");
        obj.setDisplayName(localeManager.colorize(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        activeScoreboards.put(player.getUniqueId(), sb);
        updateScoreboard(player, sb);
        player.setScoreboard(sb);
    }

    private void removeScoreboard(Player player) {
        activeScoreboards.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void updateAllScoreboards() {
        activeScoreboards.entrySet().removeIf(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                updateScoreboard(p, entry.getValue());
                return false;
            }
            return true;
        });
    }

    private void updateScoreboard(Player player, Scoreboard sb) {
        sb.getEntries().forEach(sb::resetScores);
        Objective obj = sb.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (obj == null) return;

        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUniqueId());
        obj.setDisplayName(localeManager.colorize(replacePlaceholders(config.getTitle(), player)));

        List<String> lines = config.getLines();
        int score = lines.size();
        Set<String> usedEntries = new HashSet<>();

        for (String line : lines) {
            String resolved = localeManager.colorize(replacePlaceholders(line, player));
            while (usedEntries.contains(resolved)) {
                resolved += "\u00a7r";
            }
            if (resolved.length() > SCOREBOARD_MAX_LINE_LENGTH) {
                resolved = resolved.substring(0, SCOREBOARD_MAX_LINE_LENGTH);
            }
            usedEntries.add(resolved);
            obj.getScore(resolved).setScore(score--);
        }
    }

    private String replacePlaceholders(String line, Player player) {
        UUID uuid = player.getUniqueId();
        boolean isVanished = vanished.contains(uuid);
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(uuid);

        String result = line
                .replace("{player_name}", player.getName())
                .replace("{server}", bridgeConfig.getServerName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max_players}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{date}", LocalDateTime.now().format(DATE_FORMAT))
                .replace("{vanish}", isVanished ? config.getVanish() : "")
                .replace("{vanish_status}", isVanished ? localeManager.colorize("&aON") : localeManager.colorize("&cOFF"))
                .replace("{vanished}", isVanished ? "Vanished" : "Visible")
                .replace("{staff_online}", String.valueOf(staffModeActive.size()));
        return replaceTargetPlaceholders(result, uuid);
    }

    private String replaceTargetPlaceholders(String result, UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;

        if (target != null && target.isOnline()) {
            return result
                    .replace("{target_name}", target.getName())
                    .replace("{target_health}", String.format("%.1f", target.getHealth()))
                    .replace("{target_ping}", String.valueOf(getPlayerPing(target)))
                    .replace("{freeze_status}", freezeHandler.isFrozen(targetUuid) ? localeManager.colorize("&cYes") : localeManager.colorize("&aNo"));
        }

        String nameValue = targetUuid != null ? "Offline" : "None";
        return result
                .replace("{target_name}", nameValue)
                .replace("{target_health}", PLACEHOLDER_NA)
                .replace("{target_ping}", PLACEHOLDER_NA)
                .replace("{freeze_status}", PLACEHOLDER_NA);
    }

    public boolean isInStaffMode(UUID uuid) {
        return staffModeActive.contains(uuid);
    }

    private StaffModeConfig.ScoreboardConfig getScoreboardConfig(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetScoreboard() : staffModeConfig.getStaffScoreboard();
    }

    private Map<Integer, StaffModeConfig.HotbarItem> getActiveHotbar(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetHotbar() : staffModeConfig.getStaffHotbar();
    }

    private Player resolveTarget(UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        if (targetUuid == null) return null;
        Player target = Bukkit.getPlayer(targetUuid);
        return (target != null && target.isOnline()) ? target : null;
    }

    private void runForOnlinePlayer(UUID uuid, Consumer<Player> action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }

    private void refreshScoreboard(Player player) {
        removeScoreboard(player);
        createScoreboard(player);
    }

    public void enterStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.add(uuid);

        runForOnlinePlayer(uuid, player -> {
            saveSnapshot(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.setGameMode(GameMode.CREATIVE);

            if (staffModeConfig.isVanishOnEnable()) {
                vanish(player);
            }

            setupHotbar(player, staffModeConfig.getStaffHotbar());
            createScoreboard(player);
        });
    }

    public void exitStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.remove(uuid);
        targetMap.remove(uuid);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                snapshots.remove(uuid);
                vanished.remove(uuid);
                return;
            }

            removeScoreboard(player);
            unvanish(player);
            restoreSnapshot(player);
        });
    }

    public void setTarget(String staffUuid, String targetUuid) {
        UUID staff = UUID.fromString(staffUuid);
        UUID target = UUID.fromString(targetUuid);
        targetMap.put(staff, target);

        runForOnlinePlayer(staff, staffPlayer -> {
            setupHotbar(staffPlayer, staffModeConfig.getTargetHotbar());
            refreshScoreboard(staffPlayer);

            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                staffPlayer.teleport(targetPlayer.getLocation());
            }
        });
    }

    public void clearTarget(UUID staffUuid) {
        targetMap.remove(staffUuid);

        runForOnlinePlayer(staffUuid, player -> {
            setupHotbar(player, staffModeConfig.getStaffHotbar());
            refreshScoreboard(player);
        });
    }

    public void vanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        runForOnlinePlayer(uuid, player -> {
            vanish(player);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(player, true);
            }
        });
    }

    public void unvanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        runForOnlinePlayer(uuid, player -> {
            unvanish(player);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(player, false);
            }
        });
    }

    private void setupHotbar(Player player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clear();
        boolean isVanished = vanished.contains(player.getUniqueId());

        hotbar.forEach((slot, hotbarItem) -> {
            boolean useToggle = ACTION_VANISH_TOGGLE.equals(hotbarItem.getAction())
                    && !isVanished && hotbarItem.getToggleItem() != null;
            ItemStack itemStack = buildItemStack(hotbarItem, useToggle);
            if (itemStack != null && slot >= HOTBAR_MIN_SLOT && slot <= HOTBAR_MAX_SLOT) {
                player.getInventory().setItem(slot, itemStack);
            }
        });
    }

    private void updateVanishHotbarItem(Player player, boolean nowVanished) {
        getActiveHotbar(player.getUniqueId()).entrySet().stream()
                .filter(e -> ACTION_VANISH_TOGGLE.equals(e.getValue().getAction()))
                .findFirst()
                .ifPresent(entry -> {
                    boolean useToggle = !nowVanished && entry.getValue().getToggleItem() != null;
                    player.getInventory().setItem(entry.getKey(), buildItemStack(entry.getValue(), useToggle));
                });
    }

    private ItemStack buildItemStack(StaffModeConfig.HotbarItem hotbarItem, boolean useToggle) {
        String materialName = useToggle && hotbarItem.getToggleItem() != null ? hotbarItem.getToggleItem() : hotbarItem.getItem();
        String displayName = useToggle && hotbarItem.getToggleName() != null ? hotbarItem.getToggleName() : hotbarItem.getName();
        List<String> lore = useToggle && hotbarItem.getToggleLore() != null && !hotbarItem.getToggleLore().isEmpty()
                ? hotbarItem.getToggleLore() : hotbarItem.getLore();

        Material material = parseMaterial(materialName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(localeManager.colorize(displayName));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(localeManager::colorize).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int getPlayerPing(Player player) {
        try {
            // Try modern API first (1.17+)
            return (int) Player.class.getMethod("getPing").invoke(player);
        } catch (Exception e) {
            try {
                // Fallback: CraftPlayer.getHandle().ping (NMS)
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                return handle.getClass().getField("ping").getInt(handle);
            } catch (Exception ex) {
                return -1;
            }
        }
    }

    private static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.replace("minecraft:", "").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    @SuppressWarnings("deprecation")
    private void vanish(Player staff) {
        vanished.add(staff.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(staff) && !staffModeActive.contains(online.getUniqueId())) {
                online.hidePlayer(staff);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void unvanish(Player staff) {
        vanished.remove(staff.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(staff)) {
                online.showPlayer(staff);
            }
        }
    }

    private void saveSnapshot(Player player) {
        snapshots.put(player.getUniqueId(), new PlayerSnapshot(
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

    private static ItemStack[] cloneItemArray(ItemStack[] source) {
        return Arrays.stream(source)
                .map(item -> item != null ? item.clone() : null)
                .toArray(ItemStack[]::new);
    }

    private void restoreSnapshot(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            player.getInventory().setContents(snapshot.getInventoryContents());
            player.getInventory().setArmorContents(snapshot.getArmorContents());
            player.setGameMode(snapshot.getGameMode());
            player.setHealth(Math.min(snapshot.getHealth(), player.getMaxHealth()));
            player.setFoodLevel(snapshot.getFoodLevel());
            player.setExp(snapshot.getExp());
            player.setLevel(snapshot.getLevel());
            player.teleport(snapshot.getLocation());
        } else {
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.getAction() == Action.PHYSICAL) {
            if (vanished.contains(uuid)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!staffModeActive.contains(uuid)) return;

        ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta()) {
            event.setCancelled(true);
            StaffModeConfig.HotbarItem hotbarItem = getActiveHotbar(uuid).get(player.getInventory().getHeldItemSlot());

            if (hotbarItem != null && hotbarItem.getAction() != null && !hotbarItem.getAction().isEmpty()) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    executeAction(player, hotbarItem);
                }
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && vanished.contains(uuid)) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
                org.bukkit.inventory.InventoryHolder holder = (org.bukkit.inventory.InventoryHolder) block.getState();
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

    private void executeAction(Player player, StaffModeConfig.HotbarItem hotbarItem) {
        UUID uuid = player.getUniqueId();
        String action = hotbarItem.getAction();
        if (ACTION_TARGET_SELECTOR.equals(action)) {
            // handled via entity interact
        } else if (ACTION_VANISH_TOGGLE.equals(action)) {
            toggleVanish(player);
        } else if (ACTION_STAFF_MENU.equals(action)) {
            if (bridgeClient != null && bridgeClient.isConnected()) {
                bridgeClient.sendMessage("OPEN_STAFF_MENU", uuid.toString());
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("staffmenu"));
            }
        } else if (ACTION_RANDOM_TELEPORT.equals(action)) {
            handleRandomTeleport(player);
        } else if (ACTION_FREEZE_TARGET.equals(action)) {
            handleFreezeTarget(player);
        } else if (ACTION_STOP_TARGET.equals(action)) {
            handleStopTarget(player);
        } else if (ACTION_INSPECT_TARGET.equals(action)) {
            handleInspectTarget(player);
        } else if (ACTION_OPEN_INVENTORY.equals(action)) {
            handleOpenInventory(player);
        } else if (ACTION_TELEPORT_TO_TARGET.equals(action)) {
            handleTeleportToTarget(player);
        } else if (ACTION_HACKREPORT_TARGET.equals(action)) {
            handleHackreportTarget(player);
        }
    }

    private void handleStopTarget(Player player) {
        UUID uuid = player.getUniqueId();
        Player target = resolveTarget(uuid);
        String targetName = target != null ? target.getName() : "Unknown";
        clearTarget(uuid);
        player.sendMessage(localeManager.getMessage("staff_mode.target.cleared", mapOf("player", targetName)));
    }

    private void toggleVanish(Player player) {
        if (vanished.contains(player.getUniqueId())) {
            unvanish(player);
            player.sendMessage(localeManager.getMessage("staff_mode.vanish.off"));
            updateVanishHotbarItem(player, false);
        } else {
            vanish(player);
            player.sendMessage(localeManager.getMessage("staff_mode.vanish.on"));
            updateVanishHotbarItem(player, true);
        }
    }

    private void handleRandomTeleport(Player player) {
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player) && !staffModeActive.contains(p.getUniqueId()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            player.sendMessage(localeManager.getMessage("staff_mode.random_teleport.no_players"));
            return;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        player.teleport(target.getLocation());
        player.sendMessage(localeManager.getMessage("staff_mode.random_teleport.teleported", mapOf("player", target.getName())));
    }

    private void handleFreezeTarget(Player player) {
        UUID targetUuid = targetMap.get(player.getUniqueId());
        if (targetUuid == null) return;

        Player target = Bukkit.getPlayer(targetUuid);
        String targetName = target != null ? target.getName() : "Unknown";

        if (freezeHandler.isFrozen(targetUuid)) {
            freezeHandler.unfreeze(targetUuid.toString());
            player.sendMessage(localeManager.getMessage("staff_mode.freeze.unfrozen", mapOf("player", targetName)));
        } else {
            freezeHandler.freeze(targetUuid.toString(), player.getUniqueId().toString());
            player.sendMessage(localeManager.getMessage("staff_mode.freeze.frozen", mapOf("player", targetName)));
        }
    }

    private void handleInspectTarget(Player player) {
        Player target = resolveTarget(player.getUniqueId());
        if (target == null) return;

        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage("OPEN_INSPECT_MENU", player.getUniqueId().toString(), target.getName());
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("inspect " + target.getName()));
        }
    }

    private void handleOpenInventory(Player player) {
        Player target = resolveTarget(player.getUniqueId());
        if (target != null) {
            player.openInventory(target.getInventory());
        }
    }

    private void handleHackreportTarget(Player player) {
        Player target = resolveTarget(player.getUniqueId());
        if (target != null) {
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("hackreport " + target.getName()));
        }
    }

    private void handleTeleportToTarget(Player player) {
        Player target = resolveTarget(player.getUniqueId());
        if (target != null) {
            player.teleport(target.getLocation());
            player.sendMessage(localeManager.getMessage("staff_mode.teleport.teleported", mapOf("player", target.getName())));
        } else {
            player.sendMessage(localeManager.getMessage("staff_mode.teleport.target_offline"));
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
            if (staffModeActive.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageOther(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (staffModeActive.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(org.bukkit.event.player.PlayerPickupItemEvent event) {
        if (staffModeActive.contains(event.getPlayer().getUniqueId())) {
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
            if (staffModeActive.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!staffModeActive.contains(player.getUniqueId())) return;

        Player target = resolveTarget(player.getUniqueId());
        if (target != null && event.getInventory().equals(target.getInventory())) {
            return;
        }
        if (event.getView().getTitle().startsWith(SILENT_CONTAINER_PREFIX)) {
            return;
        }
        event.setCancelled(true);
    }

    private void cancelIfInStaffMode(Player player, org.bukkit.event.Cancellable event) {
        if (staffModeActive.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!staffModeActive.contains(uuid)) return;
        event.setCancelled(true);

        if (event.getRightClicked() instanceof Player) {
            Player clicked = (Player) event.getRightClicked();
            StaffModeConfig.HotbarItem hotbarItem = getActiveHotbar(uuid).get(player.getInventory().getHeldItemSlot());

            if (hotbarItem != null && ACTION_TARGET_SELECTOR.equals(hotbarItem.getAction())) {
                setTarget(uuid.toString(), clicked.getUniqueId().toString());
                player.sendMessage(localeManager.getMessage("staff_mode.target.now_targeting", mapOf("player", clicked.getName())));
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!staffModeActive.contains(joining.getUniqueId())) {
            for (UUID vanishedUuid : vanished) {
                Player vanishedPlayer = Bukkit.getPlayer(vanishedUuid);
                if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                    joining.hidePlayer(vanishedPlayer);
                }
            }
        }
        if (vanished.contains(joining.getUniqueId())) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(joining) && !staffModeActive.contains(online.getUniqueId())) {
                    online.hidePlayer(joining);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            if (vanished.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        targetMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(uuid))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .forEach(staffUuid -> {
                    targetMap.remove(staffUuid);
                    Player staffPlayer = Bukkit.getPlayer(staffUuid);
                    if (staffPlayer != null && staffPlayer.isOnline() && staffModeActive.contains(staffUuid)) {
                        staffPlayer.sendMessage(localeManager.getMessage("staff_mode.target.disconnected", mapOf("player", player.getName())));
                        setupHotbar(staffPlayer, staffModeConfig.getStaffHotbar());
                        refreshScoreboard(staffPlayer);
                    }
                });

        if (staffModeActive.remove(uuid)) {
            removeScoreboard(player);
            unvanish(player);
            restoreSnapshot(player);
        }
        targetMap.remove(uuid);
        vanished.remove(uuid);
        snapshots.remove(uuid);
        activeScoreboards.remove(uuid);
    }

    @Getter @AllArgsConstructor
    private static class PlayerSnapshot {
        private final ItemStack[] inventoryContents;
        private final ItemStack[] armorContents;
        private final Location location;
        private final GameMode gameMode;
        private final double health;
        private final int foodLevel;
        private final float exp;
        private final int level;
    }
}
