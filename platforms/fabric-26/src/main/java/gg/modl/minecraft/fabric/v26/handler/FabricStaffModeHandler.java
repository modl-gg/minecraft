package gg.modl.minecraft.fabric.v26.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class FabricStaffModeHandler {
    private static final String SCOREBOARD_OBJECTIVE = "modl_staff";
    private static final int SCOREBOARD_MAX_LINE_LENGTH = 40;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String PLACEHOLDER_NA = "N/A";
    private static final String SILENT_CONTAINER_PREFIX = "\u00a78Viewing: ";

    private final MinecraftServer server;
    private final BridgeConfig bridgeConfig;
    private final FabricFreezeHandler freezeHandler;
    private final BridgeLocaleManager localeManager;
    private final StaffModeConfig staffModeConfig;
    private BridgeQueryClient bridgeClient;

    private final Set<UUID> staffModeActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> targetMap = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> scoreboardActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> previousScoreEntries = new ConcurrentHashMap<>();
    private ScheduledExecutorService scoreboardExecutor;

    public FabricStaffModeHandler(MinecraftServer server, BridgeConfig bridgeConfig,
                                  FabricFreezeHandler freezeHandler,
                                  BridgeLocaleManager localeManager,
                                  StaffModeConfig staffModeConfig) {
        this.server = server;
        this.bridgeConfig = bridgeConfig;
        this.freezeHandler = freezeHandler;
        this.localeManager = localeManager;
        this.staffModeConfig = staffModeConfig;
    }

    public boolean isInStaffMode(UUID uuid) {
        return staffModeActive.contains(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public void enterStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.add(uuid);

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }

        saveSnapshot(player);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);

        if (staffModeConfig.isVanishOnEnable()) {
            vanish(player);
        }

        setupHotbar(player, staffModeConfig.getStaffHotbar());
        createScoreboard(player);
    }

    public void exitStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.remove(uuid);
        targetMap.remove(uuid);

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            snapshots.remove(uuid);
            vanished.remove(uuid);
            return;
        }

        removeScoreboard(player);
        unvanish(player);
        restoreSnapshot(player);
    }

    public void setTarget(String staffUuid, String targetUuid) {
        UUID staff = UUID.fromString(staffUuid);
        UUID target = UUID.fromString(targetUuid);
        targetMap.put(staff, target);

        ServerPlayer staffPlayer = server.getPlayerList().getPlayer(staff);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(target);

        if (staffPlayer != null) {
            setupHotbar(staffPlayer, staffModeConfig.getTargetHotbar());
            refreshScoreboard(staffPlayer);
            if (targetPlayer != null) {
                staffPlayer.teleportTo((ServerLevel) targetPlayer.level(),
                        targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        Set.<Relative>of(), targetPlayer.getYRot(), targetPlayer.getXRot(), false);
            }
        }
    }

    public void clearTarget(UUID staffUuid) {
        targetMap.remove(staffUuid);
        ServerPlayer player = server.getPlayerList().getPlayer(staffUuid);
        if (player != null) {
            setupHotbar(player, staffModeConfig.getStaffHotbar());
            refreshScoreboard(player);
        }
    }

    public void vanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            vanish(player);
        }
    }

    public void unvanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            unvanish(player);
        }
    }

    private void vanish(ServerPlayer staff) {
        vanished.add(staff.getUUID());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!online.equals(staff) && !staffModeActive.contains(online.getUUID())) {
                hidePlayerFrom(staff, online);
            }
        }
        updateVanishHotbarItem(staff, true);
    }

    private void unvanish(ServerPlayer staff) {
        vanished.remove(staff.getUUID());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!online.equals(staff)) {
                showPlayerTo(staff, online);
            }
        }
        updateVanishHotbarItem(staff, false);
    }

    private void hidePlayerFrom(ServerPlayer toHide, ServerPlayer viewer) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) {
            return;
        }
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(toHide.getUUID()));
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(toHide.getId()));
    }

    private void showPlayerTo(ServerPlayer toShow, ServerPlayer viewer) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) {
            return;
        }

        com.mojang.authlib.GameProfile mojangProfile = toShow.getGameProfile();
        List<TextureProperty> textureProperties = new ArrayList<>();
        for (com.mojang.authlib.properties.Property property : mojangProfile.properties().get("textures")) {
            textureProperties.add(new TextureProperty("textures", property.value(), property.signature()));
        }
        UserProfile profile = new UserProfile(toShow.getUUID(), mojangProfile.name(), textureProperties);

        com.github.retrooper.packetevents.protocol.player.GameMode peGameMode =
                com.github.retrooper.packetevents.protocol.player.GameMode.values()[
                        toShow.gameMode.getGameModeForPlayer().ordinal()];

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, true, toShow.connection.latency(), peGameMode,
                        net.kyori.adventure.text.Component.text(toShow.getName().getString()), null
                );

        peApi.getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerPlayerInfoUpdate(
                        EnumSet.of(
                                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE
                        ),
                        info
                ));

        peApi.getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerSpawnEntity(
                        toShow.getId(),
                        Optional.of(toShow.getUUID()),
                        EntityTypes.PLAYER,
                        new Vector3d(toShow.getX(), toShow.getY(), toShow.getZ()),
                        toShow.getXRot(),
                        toShow.getYRot(),
                        toShow.getYRot(),
                        0,
                        Optional.of(new Vector3d(0, 0, 0))
                ));
    }

    private void updateVanishHotbarItem(ServerPlayer player, boolean isVanished) {
        Map<Integer, StaffModeConfig.HotbarItem> hotbar = getActiveHotbar(player.getUUID());
        if (hotbar == null) {
            return;
        }
        for (Map.Entry<Integer, StaffModeConfig.HotbarItem> entry : hotbar.entrySet()) {
            if ("vanish_toggle".equals(entry.getValue().getAction())) {
                StaffModeConfig.HotbarItem item = entry.getValue();
                String itemId = isVanished && item.getToggleItem() != null ? item.getToggleItem() : item.getItem();
                String name = isVanished && item.getToggleName() != null ? item.getToggleName() : item.getName();
                player.getInventory().setItem(entry.getKey(), createItemStack(itemId, name));
                break;
            }
        }
    }

    private void saveSnapshot(ServerPlayer player) {
        int containerSize = player.getInventory().getContainerSize();
        ItemStack[] allItems = new ItemStack[containerSize];
        for (int i = 0; i < containerSize; i++) {
            allItems[i] = player.getInventory().getItem(i).copy();
        }
        snapshots.put(player.getUUID(), new PlayerSnapshot(
                allItems,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(),
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.experienceProgress,
                player.experienceLevel
        ));
    }

    private void restoreSnapshot(ServerPlayer player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUUID());
        if (snapshot != null) {
            player.getInventory().clearContent();
            for (int i = 0; i < snapshot.inventoryContents.length && i < player.getInventory().getContainerSize(); i++) {
                player.getInventory().setItem(i, snapshot.inventoryContents[i]);
            }
            player.setGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleportTo((ServerLevel) player.level(), snapshot.x, snapshot.y, snapshot.z,
                    Set.<Relative>of(), snapshot.yaw, snapshot.pitch, false);
        } else {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private void setupHotbar(ServerPlayer player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clearContent();
        hotbar.forEach((slot, hotbarItem) -> {
            if (slot >= 0 && slot <= 8) {
                player.getInventory().setItem(slot, createItemStack(hotbarItem));
            }
        });
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem) {
        return createItemStack(hotbarItem.getItem(), hotbarItem.getName());
    }

    private ItemStack createItemStack(String itemId, String name) {
        String materialName = itemId.replace("minecraft:", "");
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", materialName);
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.getValue(id)
                : Items.STONE;
        ItemStack stack = new ItemStack(item, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(localeManager.colorize(name)));
        return stack;
    }

    public void startScoreboardUpdater() {
        scoreboardExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "modl-bridge-scoreboard");
            thread.setDaemon(true);
            return thread;
        });
        scoreboardExecutor.scheduleAtFixedRate(
                () -> server.execute(this::updateAllScoreboards), 1, 1, TimeUnit.SECONDS
        );
    }

    private void createScoreboard(ServerPlayer player) {
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUUID());
        if (!config.isEnabled()) {
            return;
        }

        var peApi = PacketEvents.getAPI();
        if (peApi == null) {
            return;
        }

        scoreboardActive.add(player.getUUID());

        String title = replacePlaceholders(config.getTitle(), player);
        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                        net.kyori.adventure.text.Component.text(localeManager.colorize(title)),
                        WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                        ScoreFormat.blankScore()
                ));
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerDisplayScoreboard(1, SCOREBOARD_OBJECTIVE));

        updateScoreboard(player);
    }

    private void removeScoreboard(ServerPlayer player) {
        if (!scoreboardActive.remove(player.getUUID())) {
            return;
        }
        previousScoreEntries.remove(player.getUUID());

        var peApi = PacketEvents.getAPI();
        if (peApi == null) {
            return;
        }

        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                        net.kyori.adventure.text.Component.empty(),
                        null
                ));
    }

    private void refreshScoreboard(ServerPlayer player) {
        if (!scoreboardActive.contains(player.getUUID())) {
            return;
        }
        removeScoreboard(player);
        createScoreboard(player);
    }

    private void updateAllScoreboards() {
        Iterator<UUID> iterator = scoreboardActive.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                iterator.remove();
                previousScoreEntries.remove(uuid);
                continue;
            }
            updateScoreboard(player);
        }
    }

    private void updateScoreboard(ServerPlayer player) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) {
            return;
        }

        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUUID());
        String title = replacePlaceholders(config.getTitle(), player);
        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                        net.kyori.adventure.text.Component.text(localeManager.colorize(title)),
                        WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                        ScoreFormat.blankScore()
                ));

        Set<String> oldEntries = previousScoreEntries.getOrDefault(player.getUUID(), Collections.emptySet());
        Set<String> newEntries = new HashSet<>();
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
            newEntries.add(resolved);

            peApi.getPlayerManager().sendPacket(player,
                    new WrapperPlayServerUpdateScore(
                            resolved,
                            WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                            SCOREBOARD_OBJECTIVE,
                            score--,
                            null,
                            ScoreFormat.blankScore()
                    ));
        }

        for (String oldEntry : oldEntries) {
            if (!newEntries.contains(oldEntry)) {
                peApi.getPlayerManager().sendPacket(player,
                        new WrapperPlayServerResetScore(oldEntry, SCOREBOARD_OBJECTIVE));
            }
        }

        previousScoreEntries.put(player.getUUID(), newEntries);
    }

    private StaffModeConfig.ScoreboardConfig getScoreboardConfig(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetScoreboard() : staffModeConfig.getStaffScoreboard();
    }

    private String replacePlaceholders(String line, ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean isVanished = vanished.contains(uuid);
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(uuid);

        String result = line
                .replace("{player_name}", player.getName().getString())
                .replace("{server}", bridgeConfig.getServerName())
                .replace("{online}", String.valueOf(server.getPlayerList().getPlayers().size()))
                .replace("{max_players}", String.valueOf(server.getPlayerList().getMaxPlayers()))
                .replace("{date}", LocalDateTime.now().format(DATE_FORMAT))
                .replace("{vanish}", isVanished ? config.getVanish() : "")
                .replace("{vanish_status}", isVanished ? localeManager.colorize("&aON") : localeManager.colorize("&cOFF"))
                .replace("{vanished}", isVanished ? "Vanished" : "Visible")
                .replace("{staff_online}", String.valueOf(staffModeActive.size()));
        return replaceTargetPlaceholders(result, uuid);
    }

    private String replaceTargetPlaceholders(String result, UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        ServerPlayer target = targetUuid != null ? server.getPlayerList().getPlayer(targetUuid) : null;

        if (target != null) {
            return result
                    .replace("{target_name}", target.getName().getString())
                    .replace("{target_health}", String.format("%.1f", target.getHealth()))
                    .replace("{target_ping}", String.valueOf(target.connection.latency()))
                    .replace("{freeze_status}", freezeHandler.isFrozen(targetUuid)
                            ? localeManager.colorize("&cYes")
                            : localeManager.colorize("&aNo"));
        }

        String nameValue = targetUuid != null ? "Offline" : "None";
        return result
                .replace("{target_name}", nameValue)
                .replace("{target_health}", PLACEHOLDER_NA)
                .replace("{target_ping}", PLACEHOLDER_NA)
                .replace("{freeze_status}", PLACEHOLDER_NA);
    }

    public void onTick() {
        if (staffModeActive.isEmpty()) {
            return;
        }
        for (UUID uuid : staffModeActive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (player.getFoodData().getFoodLevel() < 20) {
                player.getFoodData().setFoodLevel(20);
            }
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }

            Map<Integer, StaffModeConfig.HotbarItem> hotbar = getActiveHotbar(uuid);
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).isEmpty()) {
                    continue;
                }
                if (i <= 8 && hotbar != null && hotbar.containsKey(i)) {
                    continue;
                }
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public Map<Integer, StaffModeConfig.HotbarItem> getActiveHotbar(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetHotbar() : staffModeConfig.getStaffHotbar();
    }

    public void executeAction(ServerPlayer player, StaffModeConfig.HotbarItem item) {
        switch (item.getAction()) {
            case "vanish_toggle" -> toggleVanish(player);
            case "random_teleport" -> handleRandomTeleport(player);
            case "freeze_target" -> handleFreezeTarget(player);
            case "stop_target" -> handleStopTarget(player);
            case "inspect_target" -> handleInspectTarget(player);
            case "open_inventory" -> handleOpenInventory(player);
            case "teleport_to_target" -> handleTeleportToTarget(player);
            case "hackreport_target" -> handleHackreportTarget(player);
            case "staff_menu" -> handleStaffMenu(player);
        }
    }

    private void toggleVanish(ServerPlayer player) {
        if (vanished.contains(player.getUUID())) {
            unvanish(player);
            player.sendSystemMessage(Component.literal(localeManager.getMessage("staff_mode.vanish.off")), false);
        } else {
            vanish(player);
            player.sendSystemMessage(Component.literal(localeManager.getMessage("staff_mode.vanish.on")), false);
        }
    }

    private void handleRandomTeleport(ServerPlayer player) {
        List<ServerPlayer> candidates = server.getPlayerList().getPlayers().stream()
                .filter(other -> !other.equals(player) && !staffModeActive.contains(other.getUUID()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.literal(localeManager.getMessage("staff_mode.random_teleport.no_players")), false);
            return;
        }

        ServerPlayer target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        player.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
                Set.<Relative>of(), target.getYRot(), target.getXRot(), false);
        player.sendSystemMessage(Component.literal(localeManager.getMessage(
                "staff_mode.random_teleport.teleported", mapOf("player", target.getName().getString()))), false);
    }

    private void handleFreezeTarget(ServerPlayer player) {
        UUID targetUuid = targetMap.get(player.getUUID());
        if (targetUuid == null) {
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        String targetName = target != null ? target.getName().getString() : "Unknown";

        if (freezeHandler.isFrozen(targetUuid)) {
            freezeHandler.unfreeze(targetUuid.toString());
            player.sendSystemMessage(Component.literal(localeManager.getMessage(
                    "staff_mode.freeze.unfrozen", mapOf("player", targetName))), false);
        } else {
            freezeHandler.freeze(targetUuid.toString(), player.getUUID().toString());
            player.sendSystemMessage(Component.literal(localeManager.getMessage(
                    "staff_mode.freeze.frozen", mapOf("player", targetName))), false);
        }
    }

    private void handleStopTarget(ServerPlayer player) {
        ServerPlayer target = resolveTarget(player.getUUID());
        String targetName = target != null ? target.getName().getString() : "Unknown";
        clearTarget(player.getUUID());
        player.sendSystemMessage(Component.literal(localeManager.getMessage(
                "staff_mode.target.cleared", mapOf("player", targetName))), false);
    }

    private void handleInspectTarget(ServerPlayer player) {
        ServerPlayer target = resolveTarget(player.getUUID());
        if (target == null) {
            return;
        }

        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage("OPEN_INSPECT_MENU", player.getUUID().toString(), target.getName().getString());
        } else {
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), "inspect " + target.getName().getString());
        }
    }

    private void handleOpenInventory(ServerPlayer player) {
        ServerPlayer target = resolveTarget(player.getUUID());
        if (target == null) {
            return;
        }

        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, ignored) ->
                        new ChestMenu(MenuType.GENERIC_9x5, syncId, playerInventory,
                                new LivePlayerInventoryView(target), 5),
                Component.literal(target.getName().getString() + "'s Inventory")));
    }

    public void openSilentContainer(ServerPlayer player, Container container, BlockPos pos) {
        int size = container.getContainerSize();
        int rows = Math.min(6, Math.max(1, (size + 8) / 9));
        int guiSize = rows * 9;

        SimpleContainer viewInventory = new SimpleContainer(guiSize);
        for (int i = 0; i < size && i < guiSize; i++) {
            viewInventory.setItem(i, container.getItem(i).copy());
        }

        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, ignored) -> {
                    MenuType<ChestMenu> menuType = switch (rows) {
                        case 1 -> MenuType.GENERIC_9x1;
                        case 2 -> MenuType.GENERIC_9x2;
                        case 3 -> MenuType.GENERIC_9x3;
                        case 4 -> MenuType.GENERIC_9x4;
                        case 5 -> MenuType.GENERIC_9x5;
                        default -> MenuType.GENERIC_9x6;
                    };
                    return new ChestMenu(menuType, syncId, playerInventory, viewInventory, rows);
                },
                Component.literal(SILENT_CONTAINER_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ())));
    }

    private void handleTeleportToTarget(ServerPlayer player) {
        ServerPlayer target = resolveTarget(player.getUUID());
        if (target != null) {
            player.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
                    Set.<Relative>of(), target.getYRot(), target.getXRot(), false);
            player.sendSystemMessage(Component.literal(localeManager.getMessage(
                    "staff_mode.teleport.teleported", mapOf("player", target.getName().getString()))), false);
        } else {
            player.sendSystemMessage(Component.literal(localeManager.getMessage("staff_mode.teleport.target_offline")), false);
        }
    }

    private void handleHackreportTarget(ServerPlayer player) {
        ServerPlayer target = resolveTarget(player.getUUID());
        if (target != null) {
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(), "hackreport " + target.getName().getString());
        }
    }

    private void handleStaffMenu(ServerPlayer player) {
        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage("OPEN_STAFF_MENU", player.getUUID().toString());
        } else {
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "staffmenu");
        }
    }

    private ServerPlayer resolveTarget(UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        if (targetUuid == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(targetUuid);
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        this.bridgeClient = bridgeClient;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (!staffModeActive.contains(player.getUUID())) {
            server.execute(() -> {
                for (UUID vanishedUuid : vanished) {
                    ServerPlayer vanishedPlayer = server.getPlayerList().getPlayer(vanishedUuid);
                    if (vanishedPlayer != null) {
                        hidePlayerFrom(vanishedPlayer, player);
                    }
                }
            });
        }
        if (vanished.contains(player.getUUID())) {
            server.execute(() -> {
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (!online.equals(player) && !staffModeActive.contains(online.getUUID())) {
                        hidePlayerFrom(player, online);
                    }
                }
            });
        }
    }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();

        List<UUID> affectedStaff = targetMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(uuid))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID staffUuid : affectedStaff) {
            targetMap.remove(staffUuid);
            ServerPlayer staffPlayer = server.getPlayerList().getPlayer(staffUuid);
            if (staffPlayer != null && staffModeActive.contains(staffUuid)) {
                staffPlayer.sendSystemMessage(Component.literal(localeManager.getMessage(
                        "staff_mode.target.disconnected", mapOf("player", player.getName().getString()))), false);
                setupHotbar(staffPlayer, staffModeConfig.getStaffHotbar());
                refreshScoreboard(staffPlayer);
            }
        }

        if (staffModeActive.remove(uuid)) {
            removeScoreboard(player);
            unvanish(player);
            restoreSnapshot(player);
        }
        scoreboardActive.remove(uuid);
        previousScoreEntries.remove(uuid);
        targetMap.remove(uuid);
        vanished.remove(uuid);
        snapshots.remove(uuid);
    }

    public void shutdown() {
        if (scoreboardExecutor != null) {
            scoreboardExecutor.shutdownNow();
        }
        for (UUID uuid : staffModeActive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                removeScoreboard(player);
                restoreSnapshot(player);
            }
        }
        scoreboardActive.clear();
        previousScoreEntries.clear();
        staffModeActive.clear();
    }

    private static class PlayerSnapshot {
        final ItemStack[] inventoryContents;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final GameType gameMode;
        final float health;
        final int foodLevel;
        final float exp;
        final int level;

        PlayerSnapshot(ItemStack[] inventoryContents,
                       double x, double y, double z, float yaw, float pitch,
                       GameType gameMode, float health, int foodLevel, float exp, int level) {
            this.inventoryContents = inventoryContents;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.gameMode = gameMode;
            this.health = health;
            this.foodLevel = foodLevel;
            this.exp = exp;
            this.level = level;
        }
    }

    private static final class LivePlayerInventoryView implements Container {
        private static final int VIEW_SIZE = 45;

        private final ServerPlayer target;

        private LivePlayerInventoryView(ServerPlayer target) {
            this.target = target;
        }

        @Override
        public int getContainerSize() {
            return VIEW_SIZE;
        }

        @Override
        public boolean isEmpty() {
            return target.getInventory().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot < target.getInventory().getContainerSize()
                    ? target.getInventory().getItem(slot)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return slot < target.getInventory().getContainerSize()
                    ? target.getInventory().removeItem(slot, amount)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return slot < target.getInventory().getContainerSize()
                    ? target.getInventory().removeItemNoUpdate(slot)
                    : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < target.getInventory().getContainerSize()) {
                target.getInventory().setItem(slot, stack);
            }
        }

        @Override
        public void setChanged() {
            target.getInventory().setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return target.isAlive();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot < target.getInventory().getContainerSize()
                    && target.getInventory().canPlaceItem(slot, stack);
        }

        @Override
        public void clearContent() {
            target.getInventory().clearContent();
        }
    }
}
