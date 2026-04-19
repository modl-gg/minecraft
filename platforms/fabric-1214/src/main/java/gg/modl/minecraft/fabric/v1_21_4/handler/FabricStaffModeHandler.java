package gg.modl.minecraft.fabric.v1_21_4.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static gg.modl.minecraft.core.util.Java8Collections.*;

public class FabricStaffModeHandler {
    private final MinecraftServer server;
    private final BridgeConfig bridgeConfig;
    private final FabricFreezeHandler freezeHandler;
    private final BridgeLocaleManager localeManager;
    private final StaffModeConfig staffModeConfig;
    @Setter private BridgeQueryClient bridgeClient;

    private static final String SCOREBOARD_OBJECTIVE = "modl_staff";
    private static final int SCOREBOARD_MAX_LINE_LENGTH = 40;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String PLACEHOLDER_NA = "N/A";

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

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;

        saveSnapshot(player);
        player.getInventory().clear();
        player.changeGameMode(GameMode.CREATIVE);

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

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
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

        ServerPlayerEntity staffPlayer = server.getPlayerManager().getPlayer(staff);
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(target);

        if (staffPlayer != null) {
            setupHotbar(staffPlayer, staffModeConfig.getTargetHotbar());
            refreshScoreboard(staffPlayer);
            if (targetPlayer != null) {
                staffPlayer.teleport((net.minecraft.server.world.ServerWorld) targetPlayer.getEntityWorld(),
                        targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        java.util.Set.of(), targetPlayer.getYaw(), targetPlayer.getPitch(), false);
            }
        }
    }

    public void clearTarget(UUID staffUuid) {
        targetMap.remove(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(staffUuid);
        if (player != null) {
            setupHotbar(player, staffModeConfig.getStaffHotbar());
            refreshScoreboard(player);
        }
    }

    public void vanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            vanish(player);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(player, true);
            }
        }
    }

    public void unvanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            unvanish(player);
            if (staffModeActive.contains(uuid)) {
                updateVanishHotbarItem(player, false);
            }
        }
    }

    private void vanish(ServerPlayerEntity staff) {
        vanished.add(staff.getUuid());
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (!online.equals(staff) && !staffModeActive.contains(online.getUuid())) {
                hidePlayerFrom(staff, online);
            }
        }
    }

    private void unvanish(ServerPlayerEntity staff) {
        vanished.remove(staff.getUuid());
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (!online.equals(staff)) {
                showPlayerTo(staff, online);
            }
        }
    }

    private void hidePlayerFrom(ServerPlayerEntity toHide, ServerPlayerEntity viewer) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        peApi.getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerPlayerInfoRemove(toHide.getUuid()));
        peApi.getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerDestroyEntities(toHide.getId()));
    }

    private void showPlayerTo(ServerPlayerEntity toShow, ServerPlayerEntity viewer) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        com.mojang.authlib.GameProfile mojangProfile = toShow.getGameProfile();
        List<TextureProperty> textureProperties = new ArrayList<>();
        for (com.mojang.authlib.properties.Property prop : mojangProfile.getProperties().get("textures")) {
            textureProperties.add(new TextureProperty("textures", prop.value(), prop.signature()));
        }
        UserProfile profile = new UserProfile(toShow.getUuid(), mojangProfile.getName(), textureProperties);

        com.github.retrooper.packetevents.protocol.player.GameMode peGameMode =
                com.github.retrooper.packetevents.protocol.player.GameMode.values()[toShow.interactionManager.getGameMode().ordinal()];

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, toShow.networkHandler.getLatency(), peGameMode,
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
                        Optional.of(toShow.getUuid()),
                        EntityTypes.PLAYER,
                        new Vector3d(toShow.getX(), toShow.getY(), toShow.getZ()),
                        toShow.getPitch(),
                        toShow.getYaw(),
                        toShow.getYaw(),
                        0,
                        Optional.of(new Vector3d(0, 0, 0))
                ));
    }

    private void updateVanishHotbarItem(ServerPlayerEntity player, boolean isVanished) {
        Map<Integer, StaffModeConfig.HotbarItem> hotbar = getActiveHotbar(player.getUuid());
        if (hotbar == null) return;
        for (Map.Entry<Integer, StaffModeConfig.HotbarItem> entry : hotbar.entrySet()) {
            if ("vanish_toggle".equals(entry.getValue().getAction())) {
                boolean useToggle = !isVanished && entry.getValue().getToggleItem() != null;
                player.getInventory().setStack(entry.getKey(), createItemStack(entry.getValue(), useToggle));
                break;
            }
        }
    }

    private void saveSnapshot(ServerPlayerEntity player) {
        int size = player.getInventory().size();
        ItemStack[] allSlots = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            allSlots[i] = player.getInventory().getStack(i).copy();
        }
        snapshots.put(player.getUuid(), new PlayerSnapshot(
                allSlots,
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                player.interactionManager.getGameMode(),
                player.getHealth(),
                player.getHungerManager().getFoodLevel(),
                player.experienceProgress,
                player.experienceLevel
        ));
    }

    private void restoreSnapshot(ServerPlayerEntity player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUuid());
        if (snapshot != null) {
            player.getInventory().clear();
            for (int i = 0; i < snapshot.inventoryContents.length && i < player.getInventory().size(); i++) {
                player.getInventory().setStack(i, snapshot.inventoryContents[i]);
            }
            player.changeGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getHungerManager().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleport((net.minecraft.server.world.ServerWorld) player.getEntityWorld(), snapshot.x, snapshot.y, snapshot.z,
                    java.util.Set.of(), snapshot.yaw, snapshot.pitch, false);
        } else {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
        }
    }

    private void setupHotbar(ServerPlayerEntity player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clear();
        boolean isVanished = vanished.contains(player.getUuid());
        hotbar.forEach((slot, hotbarItem) -> {
            if (slot >= 0 && slot <= 8) {
                boolean useToggle = "vanish_toggle".equals(hotbarItem.getAction())
                        && !isVanished && hotbarItem.getToggleItem() != null;
                ItemStack item = createItemStack(hotbarItem, useToggle);
                player.getInventory().setStack(slot, item);
            }
        });
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem) {
        return createItemStack(hotbarItem, false);
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem, boolean useToggle) {
        String itemId = useToggle && hotbarItem.getToggleItem() != null ? hotbarItem.getToggleItem() : hotbarItem.getItem();
        String name = useToggle && hotbarItem.getToggleName() != null ? hotbarItem.getToggleName() : hotbarItem.getName();
        List<String> lore = useToggle && hotbarItem.getToggleLore() != null && !hotbarItem.getToggleLore().isEmpty()
                ? hotbarItem.getToggleLore() : hotbarItem.getLore();
        return createItemStack(itemId, name, lore);
    }

    private ItemStack createItemStack(String itemId, String name) {
        return createItemStack(itemId, name, Collections.emptyList());
    }

    private ItemStack createItemStack(String itemId, String name, List<String> lore) {
        String materialName = itemId.replace("minecraft:", "");
        Identifier id = Identifier.of("minecraft", materialName);
        net.minecraft.item.Item item = Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : Items.STONE;
        ItemStack stack = new ItemStack(item, 1);
        String displayName = localeManager.colorize(name);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        if (lore != null && !lore.isEmpty()) {
            stack.set(net.minecraft.component.DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(
                    lore.stream().<Text>map(line -> Text.literal(localeManager.colorize(line))).toList()
            ));
        }
        return stack;
    }

    public void startScoreboardUpdater() {
        scoreboardExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-bridge-scoreboard");
            t.setDaemon(true);
            return t;
        });
        scoreboardExecutor.scheduleAtFixedRate(
                () -> server.execute(this::updateAllScoreboards), 1, 1, TimeUnit.SECONDS
        );
    }

    private void createScoreboard(ServerPlayerEntity player) {
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUuid());
        if (!config.isEnabled()) return;

        var peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        scoreboardActive.add(player.getUuid());

        String title = replacePlaceholders(config.getTitle(), player);
        Component titleComponent = Component.text(localeManager.colorize(title));

        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                        titleComponent,
                        WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                        ScoreFormat.blankScore()
                ));

        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerDisplayScoreboard(1, SCOREBOARD_OBJECTIVE));

        updateScoreboard(player);
    }

    private void removeScoreboard(ServerPlayerEntity player) {
        if (!scoreboardActive.remove(player.getUuid())) return;
        previousScoreEntries.remove(player.getUuid());

        var peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                        Component.empty(),
                        null
                ));
    }

    private void refreshScoreboard(ServerPlayerEntity player) {
        if (!scoreboardActive.contains(player.getUuid())) return;
        removeScoreboard(player);
        createScoreboard(player);
    }

    private void updateAllScoreboards() {
        Iterator<UUID> it = scoreboardActive.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                it.remove();
                previousScoreEntries.remove(uuid);
                continue;
            }
            updateScoreboard(player);
        }
    }

    private void updateScoreboard(ServerPlayerEntity player) {
        var peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(player.getUuid());

        String title = replacePlaceholders(config.getTitle(), player);
        Component titleComponent = Component.text(localeManager.colorize(title));
        peApi.getPlayerManager().sendPacket(player,
                new WrapperPlayServerScoreboardObjective(
                        SCOREBOARD_OBJECTIVE,
                        WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                        titleComponent,
                        WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                        ScoreFormat.blankScore()
                ));

        Set<String> oldEntries = previousScoreEntries.getOrDefault(player.getUuid(), Collections.emptySet());
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

        previousScoreEntries.put(player.getUuid(), newEntries);
    }

    private StaffModeConfig.ScoreboardConfig getScoreboardConfig(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetScoreboard() : staffModeConfig.getStaffScoreboard();
    }

    private String replacePlaceholders(String line, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        boolean isVanished = vanished.contains(uuid);
        StaffModeConfig.ScoreboardConfig config = getScoreboardConfig(uuid);

        String result = line
                .replace("{player_name}", player.getName().getString())
                .replace("{server}", bridgeConfig.getServerName())
                .replace("{online}", String.valueOf(server.getPlayerManager().getPlayerList().size()))
                .replace("{max_players}", String.valueOf(server.getMaxPlayerCount()))
                .replace("{date}", LocalDateTime.now().format(DATE_FORMAT))
                .replace("{vanish}", isVanished ? config.getVanish() : "")
                .replace("{vanish_status}", isVanished ? localeManager.colorize("&aON") : localeManager.colorize("&cOFF"))
                .replace("{vanished}", isVanished ? "Vanished" : "Visible")
                .replace("{staff_online}", String.valueOf(staffModeActive.size()));
        return replaceTargetPlaceholders(result, uuid);
    }

    private String replaceTargetPlaceholders(String result, UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        ServerPlayerEntity target = targetUuid != null ? server.getPlayerManager().getPlayer(targetUuid) : null;

        if (target != null) {
            return result
                    .replace("{target_name}", target.getName().getString())
                    .replace("{target_health}", String.format("%.1f", target.getHealth()))
                    .replace("{target_ping}", String.valueOf(target.networkHandler.getLatency()))
                    .replace("{freeze_status}", freezeHandler.isFrozen(targetUuid) ? localeManager.colorize("&cYes") : localeManager.colorize("&aNo"));
        }

        String nameValue = targetUuid != null ? "Offline" : "None";
        return result
                .replace("{target_name}", nameValue)
                .replace("{target_health}", PLACEHOLDER_NA)
                .replace("{target_ping}", PLACEHOLDER_NA)
                .replace("{freeze_status}", PLACEHOLDER_NA);
    }

    public void onTick() {
        if (!vanished.isEmpty()) {
            clearVanishedMobTargets();
        }
        if (staffModeActive.isEmpty()) return;
        for (UUID uuid : staffModeActive) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            if (player.getHungerManager().getFoodLevel() < 20) {
                player.getHungerManager().setFoodLevel(20);
            }
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            Map<Integer, StaffModeConfig.HotbarItem> hotbar = getActiveHotbar(uuid);
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (player.getInventory().getStack(i).isEmpty()) continue;
                if (i <= 8 && hotbar != null && hotbar.containsKey(i)) continue;
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    private void clearVanishedMobTargets() {
        for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
            for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
                if (!(entity instanceof net.minecraft.entity.mob.MobEntity mob)) {
                    continue;
                }
                net.minecraft.entity.LivingEntity target = mob.getTarget();
                if (target instanceof ServerPlayerEntity player && vanished.contains(player.getUuid())) {
                    mob.setTarget(null);
                }
            }
        }
    }

    public Map<Integer, StaffModeConfig.HotbarItem> getActiveHotbar(UUID uuid) {
        return targetMap.containsKey(uuid) ? staffModeConfig.getTargetHotbar() : staffModeConfig.getStaffHotbar();
    }

    public void executeAction(ServerPlayerEntity player, StaffModeConfig.HotbarItem item) {
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

    private void toggleVanish(ServerPlayerEntity player) {
        if (vanished.contains(player.getUuid())) {
            unvanish(player);
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.vanish.off")), false);
            updateVanishHotbarItem(player, false);
        } else {
            vanish(player);
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.vanish.on")), false);
            updateVanishHotbarItem(player, true);
        }
    }

    private void handleRandomTeleport(ServerPlayerEntity player) {
        List<ServerPlayerEntity> candidates = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !p.equals(player) && !staffModeActive.contains(p.getUuid()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.random_teleport.no_players")), false);
            return;
        }

        ServerPlayerEntity target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        player.teleport((net.minecraft.server.world.ServerWorld) target.getEntityWorld(), target.getX(), target.getY(), target.getZ(),
                java.util.Set.of(), target.getYaw(), target.getPitch(), false);
        player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.random_teleport.teleported",
                mapOf("player", target.getName().getString()))), false);
    }

    private void handleFreezeTarget(ServerPlayerEntity player) {
        UUID targetUuid = targetMap.get(player.getUuid());
        if (targetUuid == null) return;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        String targetName = target != null ? target.getName().getString() : "Unknown";

        if (freezeHandler.isFrozen(targetUuid)) {
            freezeHandler.unfreeze(targetUuid.toString());
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.freeze.unfrozen",
                    mapOf("player", targetName))), false);
        } else {
            freezeHandler.freeze(targetUuid.toString(), player.getUuid().toString());
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.freeze.frozen",
                    mapOf("player", targetName))), false);
        }
    }

    private void handleStopTarget(ServerPlayerEntity player) {
        ServerPlayerEntity target = resolveTarget(player.getUuid());
        String targetName = target != null ? target.getName().getString() : "Unknown";
        clearTarget(player.getUuid());
        player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.target.cleared",
                mapOf("player", targetName))), false);
    }

    private void handleInspectTarget(ServerPlayerEntity player) {
        ServerPlayerEntity target = resolveTarget(player.getUuid());
        if (target == null) return;

        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage("OPEN_INSPECT_MENU", player.getUuid().toString(), target.getName().getString());
        } else {
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "inspect " + target.getName().getString());
        }
    }

    private void handleOpenInventory(ServerPlayerEntity player) {
        ServerPlayerEntity target = resolveTarget(player.getUuid());
        if (target == null) return;

        player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new net.minecraft.screen.GenericContainerScreenHandler(
                        net.minecraft.screen.ScreenHandlerType.GENERIC_9X5, syncId, playerInv,
                        new LivePlayerInventoryView(target), 5),
                Text.literal(target.getName().getString() + "'s Inventory")));
    }

    private static final String SILENT_CONTAINER_PREFIX = "\u00a78Viewing: ";

    public void openSilentContainer(ServerPlayerEntity player, net.minecraft.inventory.Inventory container, net.minecraft.util.math.BlockPos pos) {
        int size = container.size();
        int rows = Math.min(6, Math.max(1, (size + 8) / 9));
        int guiSize = rows * 9;

        net.minecraft.inventory.SimpleInventory viewInventory = new net.minecraft.inventory.SimpleInventory(guiSize);
        for (int i = 0; i < size && i < guiSize; i++) {
            viewInventory.setStack(i, container.getStack(i).copy());
        }

        net.minecraft.screen.ScreenHandlerType<?> handlerType = switch (rows) {
            case 1 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X1;
            case 2 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X2;
            case 3 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X3;
            case 4 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X4;
            case 5 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X5;
            default -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X6;
        };

        player.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new net.minecraft.screen.GenericContainerScreenHandler(
                        handlerType, syncId, playerInv, viewInventory, rows),
                Text.literal(SILENT_CONTAINER_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ())));
    }

    private void handleTeleportToTarget(ServerPlayerEntity player) {
        ServerPlayerEntity target = resolveTarget(player.getUuid());
        if (target != null) {
            player.teleport((net.minecraft.server.world.ServerWorld) target.getEntityWorld(), target.getX(), target.getY(), target.getZ(),
                    java.util.Set.of(), target.getYaw(), target.getPitch(), false);
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.teleport.teleported",
                    mapOf("player", target.getName().getString()))), false);
        } else {
            player.sendMessage(Text.literal(localeManager.getMessage("staff_mode.teleport.target_offline")), false);
        }
    }

    private void handleHackreportTarget(ServerPlayerEntity player) {
        ServerPlayerEntity target = resolveTarget(player.getUuid());
        if (target != null) {
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "hackreport " + target.getName().getString());
        }
    }

    private void handleStaffMenu(ServerPlayerEntity player) {
        if (bridgeClient != null && bridgeClient.isConnected()) {
            bridgeClient.sendMessage("OPEN_STAFF_MENU", player.getUuid().toString());
        } else {
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "staffmenu");
        }
    }

    private ServerPlayerEntity resolveTarget(UUID staffUuid) {
        UUID targetUuid = targetMap.get(staffUuid);
        if (targetUuid == null) return null;
        return server.getPlayerManager().getPlayer(targetUuid);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (!staffModeActive.contains(player.getUuid())) {
            server.execute(() -> {
                for (UUID vanishedUuid : vanished) {
                    ServerPlayerEntity vanishedPlayer = server.getPlayerManager().getPlayer(vanishedUuid);
                    if (vanishedPlayer != null) {
                        hidePlayerFrom(vanishedPlayer, player);
                    }
                }
            });
        }
        if (vanished.contains(player.getUuid())) {
            server.execute(() -> {
                for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                    if (!online.equals(player) && !staffModeActive.contains(online.getUuid())) {
                        hidePlayerFrom(player, online);
                    }
                }
            });
        }
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        List<UUID> affectedStaff = targetMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(uuid))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID staffUuid : affectedStaff) {
            targetMap.remove(staffUuid);
            ServerPlayerEntity staffPlayer = server.getPlayerManager().getPlayer(staffUuid);
            if (staffPlayer != null && staffModeActive.contains(staffUuid)) {
                staffPlayer.sendMessage(Text.literal(localeManager.getMessage("staff_mode.target.disconnected",
                        mapOf("player", player.getName().getString()))), false);
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
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
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
        final double x, y, z;
        final float yaw, pitch;
        final GameMode gameMode;
        final float health;
        final int foodLevel;
        final float exp;
        final int level;

        PlayerSnapshot(ItemStack[] inventoryContents,
                       double x, double y, double z, float yaw, float pitch,
                       GameMode gameMode, float health, int foodLevel, float exp, int level) {
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

    private static final class LivePlayerInventoryView implements net.minecraft.inventory.Inventory {
        private static final int VIEW_SIZE = 45;

        private final ServerPlayerEntity target;

        private LivePlayerInventoryView(ServerPlayerEntity target) {
            this.target = target;
        }

        @Override
        public int size() {
            return VIEW_SIZE;
        }

        @Override
        public boolean isEmpty() {
            return target.getInventory().isEmpty();
        }

        @Override
        public ItemStack getStack(int slot) {
            return slot < target.getInventory().size() ? target.getInventory().getStack(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            return slot < target.getInventory().size() ? target.getInventory().removeStack(slot, amount) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            return slot < target.getInventory().size() ? target.getInventory().removeStack(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (slot < target.getInventory().size()) {
                target.getInventory().setStack(slot, stack);
            }
        }

        @Override
        public void markDirty() {
            target.getInventory().markDirty();
        }

        @Override
        public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
            return target.isAlive();
        }

        @Override
        public boolean isValid(int slot, ItemStack stack) {
            return slot < target.getInventory().size() && target.getInventory().isValid(slot, stack);
        }

        @Override
        public void clear() {
            target.getInventory().clear();
        }
    }
}
