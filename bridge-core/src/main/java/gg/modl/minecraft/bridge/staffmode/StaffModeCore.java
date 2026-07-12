package gg.modl.minecraft.bridge.staffmode;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig.HotbarItem;
import gg.modl.minecraft.bridge.config.StaffModeConfig.ScoreboardConfig;
import gg.modl.minecraft.bridge.freeze.FreezeCore;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class StaffModeCore {
    private static final int SCOREBOARD_MAX_LINE_LENGTH = 40;
    private static final int HOTBAR_MIN_SLOT = 0;
    private static final int HOTBAR_MAX_SLOT = 8;
    private static final String PLACEHOLDER_NA = "N/A";
    private static final String UNKNOWN_NAME = "Unknown";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

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

    private final BridgeConfig bridgeConfig;
    private final StaffModeConfig staffModeConfig;
    private final BridgeLocaleManager localeManager;
    private final BridgeScheduler scheduler;
    private final FreezeCore freezeCore;
    private final StaffModeOps ops;

    @Setter private BridgeQueryClient bridgeClient;

    private final Set<UUID> staffModeActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> targetByStaff = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> scoreboardActive = ConcurrentHashMap.newKeySet();
    private final Map<String, Consumer<UUID>> actionHandlers = buildActionHandlers();

    private BridgeTask scoreboardTimer;

    public StaffModeCore(BridgeConfig bridgeConfig, StaffModeConfig staffModeConfig,
                         BridgeLocaleManager localeManager, BridgeScheduler scheduler,
                         FreezeCore freezeCore, StaffModeOps ops) {
        this.bridgeConfig = bridgeConfig;
        this.staffModeConfig = staffModeConfig;
        this.localeManager = localeManager;
        this.scheduler = scheduler;
        this.freezeCore = freezeCore;
        this.ops = ops;
    }

    public void start() {
        scoreboardTimer = scheduler.runTimerAsync(
                () -> scheduler.runOnMainThread(this::tickScoreboards), 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (scoreboardTimer != null) {
            scoreboardTimer.cancel();
            scoreboardTimer = null;
        }
        for (UUID uuid : staffModeActive) {
            if (ops.isOnline(uuid)) {
                ops.restoreSnapshot(uuid);
                removeScoreboard(uuid);
            }
        }
        for (UUID uuid : vanished) {
            if (ops.isOnline(uuid)) {
                unvanish(uuid);
            }
        }
        scoreboardActive.clear();
        staffModeActive.clear();
        vanished.clear();
        targetByStaff.clear();
        ops.clearSnapshots();
        ops.clearScoreboards();
    }

    public boolean isInStaffMode(UUID uuid) {
        return staffModeActive.contains(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public void enterStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        if (staffModeActive.contains(uuid)) return;

        scheduler.runForPlayer(uuid, () -> {
            if (!ops.isOnline(uuid)) return;
            if (!staffModeActive.add(uuid)) return;
            applyStaffModeSetup(uuid);
        });
    }

    public void exitStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.remove(uuid);
        targetByStaff.remove(uuid);

        scheduler.runForPlayer(uuid, () -> {
            if (!ops.isOnline(uuid)) {
                vanished.remove(uuid);
                return;
            }

            removeScoreboard(uuid);
            unvanish(uuid);
            ops.restoreSnapshot(uuid);

            for (UUID vanishedUuid : vanished) {
                if (vanishedUuid.equals(uuid)) continue;
                if (ops.isOnline(vanishedUuid)) {
                    ops.hidePlayer(uuid, vanishedUuid);
                }
            }
        });
    }

    public void setTarget(String staffUuid, String targetUuid) {
        UUID staff = UUID.fromString(staffUuid);
        UUID target = UUID.fromString(targetUuid);
        targetByStaff.put(staff, target);

        scheduler.runForPlayer(staff, () -> {
            if (!ops.isOnline(staff)) return;
            setupHotbar(staff, staffModeConfig.getTargetHotbar());
            refreshScoreboard(staff);
            if (ops.isOnline(target)) {
                ops.teleportToPlayer(staff, target);
            }
        });
    }

    public void clearTarget(UUID staffUuid) {
        targetByStaff.remove(staffUuid);

        scheduler.runForPlayer(staffUuid, () -> {
            if (!ops.isOnline(staffUuid)) return;
            setupHotbar(staffUuid, staffModeConfig.getStaffHotbar());
            refreshScoreboard(staffUuid);
        });
    }

    public void vanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        scheduler.runForPlayer(uuid, () -> {
            if (!ops.isOnline(uuid)) return;
            vanish(uuid);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(uuid, true);
            }
        });
    }

    public void unvanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        scheduler.runForPlayer(uuid, () -> {
            if (!ops.isOnline(uuid)) return;
            unvanish(uuid);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(uuid, false);
            }
        });
    }

    public void handleHotbarAction(UUID staffUuid, int heldSlot) {
        HotbarItem item = getActiveHotbar(staffUuid).get(heldSlot);
        if (item != null && item.getAction() != null && !item.getAction().isEmpty()) {
            executeAction(staffUuid, item);
        }
    }

    public void handleTargetSelect(UUID staffUuid, int heldSlot, UUID clickedUuid) {
        HotbarItem item = getActiveHotbar(staffUuid).get(heldSlot);
        if (item != null && ACTION_TARGET_SELECTOR.equals(item.getAction())) {
            setTarget(staffUuid.toString(), clickedUuid.toString());
            ops.sendMessage(staffUuid, localeManager.getMessage("staff_mode.target.now_targeting",
                    mapOf("player", ops.playerName(clickedUuid))));
        }
    }

    public void handlePlayerJoin(UUID uuid) {
        if (staffModeActive.contains(uuid) && !ops.hasSnapshot(uuid)) {
            scheduler.runForPlayer(uuid, () -> {
                if (ops.isOnline(uuid)) applyStaffModeSetup(uuid);
            });
        }
        if (!staffModeActive.contains(uuid) && ops.hasSnapshot(uuid)) {
            scheduler.runForPlayer(uuid, () -> {
                if (ops.isOnline(uuid)) ops.restoreSnapshot(uuid);
            });
        }

        UUID targetUuid = targetByStaff.get(uuid);
        if (targetUuid != null) {
            setTarget(uuid.toString(), targetUuid.toString());
        }

        if (!staffModeActive.contains(uuid)) {
            for (UUID vanishedUuid : vanished) {
                if (ops.isOnline(vanishedUuid)) {
                    ops.hidePlayer(uuid, vanishedUuid);
                }
            }
        }
        if (vanished.contains(uuid)) {
            for (UUID online : ops.onlinePlayerUuids()) {
                if (!online.equals(uuid) && !staffModeActive.contains(online)) {
                    ops.hidePlayer(online, uuid);
                }
            }
        }
    }

    public void handlePlayerQuit(UUID uuid) {
        String quitterName = ops.playerName(uuid);

        List<UUID> affectedStaff = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : targetByStaff.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                affectedStaff.add(entry.getKey());
            }
        }
        for (UUID staffUuid : affectedStaff) {
            targetByStaff.remove(staffUuid);
            if (ops.isOnline(staffUuid) && staffModeActive.contains(staffUuid)) {
                ops.sendMessage(staffUuid, localeManager.getMessage("staff_mode.target.disconnected",
                        mapOf("player", quitterName)));
                setupHotbar(staffUuid, staffModeConfig.getStaffHotbar());
                refreshScoreboard(staffUuid);
            }
        }

        if (staffModeActive.remove(uuid)) {
            removeScoreboard(uuid);
            unvanish(uuid);
            ops.restoreSnapshot(uuid);
        }
        targetByStaff.remove(uuid);
        vanished.remove(uuid);
        ops.discardSnapshot(uuid);
        discardScoreboard(uuid);
    }

    private void applyStaffModeSetup(UUID uuid) {
        ops.saveSnapshot(uuid);
        ops.clearInventory(uuid);
        ops.clearArmor(uuid);
        ops.setGameMode(uuid, StaffGameMode.CREATIVE);

        if (staffModeConfig.isVanishOnEnable()) {
            vanish(uuid);
        }

        setupHotbar(uuid, staffModeConfig.getStaffHotbar());
        createScoreboard(uuid);
    }

    private void setupHotbar(UUID uuid, Map<Integer, HotbarItem> hotbar) {
        ops.clearInventory(uuid);
        boolean isVanished = vanished.contains(uuid);

        for (Map.Entry<Integer, HotbarItem> entry : hotbar.entrySet()) {
            int slot = entry.getKey();
            if (slot < HOTBAR_MIN_SLOT || slot > HOTBAR_MAX_SLOT) continue;
            HotbarItem item = entry.getValue();
            boolean useToggle = ACTION_VANISH_TOGGLE.equals(item.getAction())
                    && !isVanished && item.getToggleItem() != null;
            applyHotbarSlot(uuid, slot, item, useToggle);
        }
    }

    private void updateVanishHotbarItem(UUID uuid, boolean nowVanished) {
        for (Map.Entry<Integer, HotbarItem> entry : getActiveHotbar(uuid).entrySet()) {
            if (ACTION_VANISH_TOGGLE.equals(entry.getValue().getAction())) {
                boolean useToggle = !nowVanished && entry.getValue().getToggleItem() != null;
                applyHotbarSlot(uuid, entry.getKey(), entry.getValue(), useToggle);
                return;
            }
        }
    }

    private void applyHotbarSlot(UUID uuid, int slot, HotbarItem item, boolean useToggle) {
        String materialId = useToggle && item.getToggleItem() != null ? item.getToggleItem() : item.getItem();
        String name = useToggle && item.getToggleName() != null ? item.getToggleName() : item.getName();
        List<String> lore = useToggle && item.getToggleLore() != null && !item.getToggleLore().isEmpty()
                ? item.getToggleLore() : item.getLore();

        String displayName = localeManager.colorize(name);
        List<String> colorizedLore = lore == null ? null
                : lore.stream().map(localeManager::colorize).collect(Collectors.toList());
        ops.setHotbarSlot(uuid, slot, materialId, displayName, colorizedLore);
    }

    private Map<Integer, HotbarItem> getActiveHotbar(UUID uuid) {
        return targetByStaff.containsKey(uuid) ? staffModeConfig.getTargetHotbar() : staffModeConfig.getStaffHotbar();
    }

    private void vanish(UUID uuid) {
        vanished.add(uuid);
        for (UUID online : ops.onlinePlayerUuids()) {
            if (!online.equals(uuid) && !staffModeActive.contains(online)) {
                ops.hidePlayer(online, uuid);
            }
        }
    }

    private void unvanish(UUID uuid) {
        vanished.remove(uuid);
        for (UUID online : ops.onlinePlayerUuids()) {
            if (!online.equals(uuid)) {
                ops.showPlayer(online, uuid);
            }
        }
    }

    private UUID resolveTarget(UUID staffUuid) {
        UUID targetUuid = targetByStaff.get(staffUuid);
        if (targetUuid == null) return null;
        return ops.isOnline(targetUuid) ? targetUuid : null;
    }

    private Map<String, Consumer<UUID>> buildActionHandlers() {
        Map<String, Consumer<UUID>> handlers = new HashMap<>();
        handlers.put(ACTION_VANISH_TOGGLE, this::toggleVanish);
        handlers.put(ACTION_STAFF_MENU, this::openStaffMenu);
        handlers.put(ACTION_RANDOM_TELEPORT, this::handleRandomTeleport);
        handlers.put(ACTION_FREEZE_TARGET, this::handleFreezeTarget);
        handlers.put(ACTION_STOP_TARGET, this::handleStopTarget);
        handlers.put(ACTION_INSPECT_TARGET, this::handleInspectTarget);
        handlers.put(ACTION_OPEN_INVENTORY, this::handleOpenInventory);
        handlers.put(ACTION_TELEPORT_TO_TARGET, this::handleTeleportToTarget);
        handlers.put(ACTION_HACKREPORT_TARGET, this::handleHackreportTarget);
        return handlers;
    }

    private void executeAction(UUID uuid, HotbarItem item) {
        Consumer<UUID> handler = actionHandlers.get(item.getAction());
        if (handler != null) {
            handler.accept(uuid);
        }
    }

    private void toggleVanish(UUID uuid) {
        if (vanished.contains(uuid)) {
            unvanish(uuid);
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.vanish.off"));
            updateVanishHotbarItem(uuid, false);
        } else {
            vanish(uuid);
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.vanish.on"));
            updateVanishHotbarItem(uuid, true);
        }
    }

    private void openStaffMenu(UUID uuid) {
        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage(BridgeAction.OPEN_STAFF_MENU.wire(), uuid.toString());
        } else {
            scheduler.runForPlayer(uuid, () -> ops.runPlayerCommand(uuid, "staffmenu"));
        }
    }

    private void handleStopTarget(UUID uuid) {
        UUID target = resolveTarget(uuid);
        String targetName = target != null ? ops.playerName(target) : UNKNOWN_NAME;
        clearTarget(uuid);
        ops.sendMessage(uuid, localeManager.getMessage("staff_mode.target.cleared", mapOf("player", targetName)));
    }

    private void handleRandomTeleport(UUID uuid) {
        List<UUID> candidates = new ArrayList<>();
        for (UUID online : ops.onlinePlayerUuids()) {
            if (!online.equals(uuid) && !staffModeActive.contains(online)) {
                candidates.add(online);
            }
        }

        if (candidates.isEmpty()) {
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.random_teleport.no_players"));
            return;
        }

        UUID target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ops.teleportToPlayer(uuid, target);
        ops.sendMessage(uuid, localeManager.getMessage("staff_mode.random_teleport.teleported",
                mapOf("player", ops.playerName(target))));
    }

    private void handleFreezeTarget(UUID uuid) {
        UUID targetUuid = targetByStaff.get(uuid);
        if (targetUuid == null) return;

        String targetName = ops.playerName(targetUuid);
        if (targetName == null) targetName = UNKNOWN_NAME;

        if (freezeCore.isFrozen(targetUuid)) {
            freezeCore.unfreeze(targetUuid.toString());
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.freeze.unfrozen", mapOf("player", targetName)));
        } else {
            freezeCore.freeze(targetUuid.toString(), uuid.toString());
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.freeze.frozen", mapOf("player", targetName)));
        }
    }

    private void handleInspectTarget(UUID uuid) {
        UUID target = resolveTarget(uuid);
        if (target == null) return;

        String targetName = ops.playerName(target);
        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage(BridgeAction.OPEN_INSPECT_MENU.wire(), uuid.toString(), targetName);
        } else {
            scheduler.runForPlayer(uuid, () -> ops.runPlayerCommand(uuid, "inspect " + targetName));
        }
    }

    private void handleOpenInventory(UUID uuid) {
        UUID target = resolveTarget(uuid);
        if (target != null) {
            ops.openTargetInventory(uuid, target);
        }
    }

    private void handleHackreportTarget(UUID uuid) {
        UUID target = resolveTarget(uuid);
        if (target != null) {
            String targetName = ops.playerName(target);
            scheduler.runForPlayer(uuid, () -> ops.runPlayerCommand(uuid, "hackreport " + targetName));
        }
    }

    private void handleTeleportToTarget(UUID uuid) {
        UUID target = resolveTarget(uuid);
        if (target != null) {
            ops.teleportToPlayer(uuid, target);
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.teleport.teleported",
                    mapOf("player", ops.playerName(target))));
        } else {
            ops.sendMessage(uuid, localeManager.getMessage("staff_mode.teleport.target_offline"));
        }
    }

    private void createScoreboard(UUID uuid) {
        ScoreboardConfig config = getScoreboardConfig(uuid);
        if (!config.isEnabled()) return;
        scoreboardActive.add(uuid);
        ops.createScoreboard(uuid, buildContent(uuid, config));
    }

    private void removeScoreboard(UUID uuid) {
        scoreboardActive.remove(uuid);
        ops.removeScoreboard(uuid);
    }

    private void refreshScoreboard(UUID uuid) {
        removeScoreboard(uuid);
        createScoreboard(uuid);
    }

    private void discardScoreboard(UUID uuid) {
        scoreboardActive.remove(uuid);
        ops.discardScoreboard(uuid);
    }

    private void tickScoreboards() {
        Iterator<UUID> it = scoreboardActive.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            if (!ops.isOnline(uuid)) {
                it.remove();
                ops.discardScoreboard(uuid);
                continue;
            }
            ops.updateScoreboard(uuid, buildContent(uuid, getScoreboardConfig(uuid)));
        }
    }

    private ScoreboardContent buildContent(UUID uuid, ScoreboardConfig config) {
        String title = localeManager.colorize(replacePlaceholders(config.getTitle(), uuid));

        List<String> configLines = config.getLines();
        List<ScoreboardContent.Line> lines = new ArrayList<>();
        int score = configLines.size();
        Set<String> usedEntries = new HashSet<>();

        for (String line : configLines) {
            String resolved = localeManager.colorize(replacePlaceholders(line, uuid));
            String entry = uniqueEntry(truncateColorSafe(resolved, SCOREBOARD_MAX_LINE_LENGTH), usedEntries);
            usedEntries.add(entry);
            lines.add(new ScoreboardContent.Line(entry, score--));
        }
        return new ScoreboardContent(title, lines);
    }

    private static String uniqueEntry(String resolved, Set<String> usedEntries) {
        String candidate = resolved;
        StringBuilder suffix = new StringBuilder();
        while (usedEntries.contains(candidate) && suffix.length() < SCOREBOARD_MAX_LINE_LENGTH) {
            suffix.append('§').append('r');
            candidate = truncateColorSafe(resolved, SCOREBOARD_MAX_LINE_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
    }

    private static String truncateColorSafe(String s, int n) {
        if (s == null || s.length() <= n) return s;
        if (n > 0 && s.charAt(n - 1) == '§') return s.substring(0, n - 1);
        return s.substring(0, n);
    }

    private String replacePlaceholders(String line, UUID uuid) {
        boolean isVanished = vanished.contains(uuid);
        ScoreboardConfig config = getScoreboardConfig(uuid);

        String result = line
                .replace("{player_name}", ops.playerName(uuid))
                .replace("{server}", bridgeConfig.getServerName())
                .replace("{online}", String.valueOf(ops.onlinePlayerCount()))
                .replace("{max_players}", String.valueOf(ops.maxPlayerCount()))
                .replace("{date}", LocalDateTime.now().format(DATE_FORMAT))
                .replace("{vanish}", isVanished ? config.getVanish() : "")
                .replace("{vanish_status}", isVanished ? localeManager.colorize("&aON") : localeManager.colorize("&cOFF"))
                .replace("{vanished}", isVanished ? "Vanished" : "Visible")
                .replace("{staff_online}", String.valueOf(staffModeActive.size()));
        return replaceTargetPlaceholders(result, uuid);
    }

    private String replaceTargetPlaceholders(String result, UUID staffUuid) {
        UUID targetUuid = targetByStaff.get(staffUuid);

        if (targetUuid != null && ops.isOnline(targetUuid)) {
            return result
                    .replace("{target_name}", ops.playerName(targetUuid))
                    .replace("{target_health}", String.format("%.1f", ops.playerHealth(targetUuid)))
                    .replace("{target_ping}", String.valueOf(ops.playerPing(targetUuid)))
                    .replace("{freeze_status}", freezeCore.isFrozen(targetUuid)
                            ? localeManager.colorize("&cYes") : localeManager.colorize("&aNo"));
        }

        String nameValue = targetUuid != null ? "Offline" : "None";
        return result
                .replace("{target_name}", nameValue)
                .replace("{target_health}", PLACEHOLDER_NA)
                .replace("{target_ping}", PLACEHOLDER_NA)
                .replace("{freeze_status}", PLACEHOLDER_NA);
    }

    private ScoreboardConfig getScoreboardConfig(UUID uuid) {
        return targetByStaff.containsKey(uuid) ? staffModeConfig.getTargetScoreboard() : staffModeConfig.getStaffScoreboard();
    }
}
