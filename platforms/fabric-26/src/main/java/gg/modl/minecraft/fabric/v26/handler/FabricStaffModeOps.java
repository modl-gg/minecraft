package gg.modl.minecraft.fabric.v26.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
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
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import gg.modl.minecraft.bridge.staffmode.ScoreboardContent;
import gg.modl.minecraft.bridge.staffmode.StaffGameMode;
import gg.modl.minecraft.bridge.staffmode.StaffModeOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class FabricStaffModeOps implements StaffModeOps {
    private static final String SCOREBOARD_OBJECTIVE = "modl_staff";
    private static final int TARGET_INVENTORY_SIZE = 45;

    private final MinecraftServer server;
    private final Logger logger;
    private final Set<String> warnedItemIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> previousScoreEntries = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> hiddenFromViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> hotbarSlots = new ConcurrentHashMap<>();

    FabricStaffModeOps(MinecraftServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    private ServerPlayer player(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return player(uuid) != null;
    }

    @Override
    public String playerName(UUID uuid) {
        ServerPlayer player = player(uuid);
        return player != null ? player.getName().getString() : null;
    }

    @Override
    public Set<UUID> onlinePlayerUuids() {
        return server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID).collect(Collectors.toSet());
    }

    @Override
    public int onlinePlayerCount() {
        return server.getPlayerList().getPlayers().size();
    }

    @Override
    public int maxPlayerCount() {
        return server.getPlayerList().getMaxPlayers();
    }

    @Override
    public int playerPing(UUID uuid) {
        ServerPlayer player = player(uuid);
        return player != null ? player.connection.latency() : -1;
    }

    @Override
    public double playerHealth(UUID uuid) {
        ServerPlayer player = player(uuid);
        return player != null ? player.getHealth() : 0.0;
    }

    @Override
    public void clearInventory(UUID uuid) {
        hotbarSlots.remove(uuid);
        ServerPlayer player = player(uuid);
        if (player == null) return;
        player.getInventory().clearContent();
    }

    boolean isProtectedSlot(UUID uuid, int slot) {
        Set<Integer> slots = hotbarSlots.get(uuid);
        return slots != null && slots.contains(slot);
    }

    @Override
    public void clearArmor(UUID uuid) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
    }

    @Override
    public void setGameMode(UUID uuid, StaffGameMode mode) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        player.setGameMode(mode == StaffGameMode.CREATIVE ? GameType.CREATIVE : GameType.SURVIVAL);
    }

    @Override
    public void setHotbarSlot(UUID uuid, int slot, String materialId, String displayName, List<String> lore) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        hotbarSlots.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(slot);
        player.getInventory().setItem(slot, buildItem(materialId, displayName, lore));
    }

    private ItemStack buildItem(String materialId, String displayName, List<String> lore) {
        String materialName = materialId.replace("minecraft:", "");
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", materialName);
        Item item;
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            item = BuiltInRegistries.ITEM.getValue(id);
        } else {
            if (warnedItemIds.add(materialId)) {
                logger.warn("[staff-mode] Unknown hotbar item id '{}', using STONE", materialId);
            }
            item = Items.STONE;
        }
        ItemStack stack = new ItemStack(item, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(
                    lore.stream().<Component>map(Component::literal).toList()));
        }
        return stack;
    }

    @Override
    public boolean hasSnapshot(UUID uuid) {
        return snapshots.containsKey(uuid);
    }

    @Override
    public void saveSnapshot(UUID uuid) {
        ServerPlayer player = player(uuid);
        if (player == null || snapshots.containsKey(uuid)) return;
        int size = player.getInventory().getContainerSize();
        ItemStack[] allSlots = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            allSlots[i] = player.getInventory().getItem(i).copy();
        }
        snapshots.put(uuid, new PlayerSnapshot(
                allSlots,
                ((ServerLevel) player.level()).dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(),
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.experienceProgress,
                player.experienceLevel));
    }

    @Override
    public void restoreSnapshot(UUID uuid) {
        hotbarSlots.remove(uuid);
        ServerPlayer player = player(uuid);
        if (player == null) {
            snapshots.remove(uuid);
            return;
        }
        PlayerSnapshot snapshot = snapshots.remove(uuid);
        if (snapshot != null) {
            player.getInventory().clearContent();
            for (int i = 0; i < snapshot.getInventoryContents().length && i < player.getInventory().getContainerSize(); i++) {
                player.getInventory().setItem(i, snapshot.getInventoryContents()[i].copy());
            }
            player.setGameMode(snapshot.getGameMode());
            player.setHealth(Math.min(snapshot.getHealth(), player.getMaxHealth()));
            player.getFoodData().setFoodLevel(snapshot.getFoodLevel());
            player.experienceProgress = snapshot.getExp();
            player.experienceLevel = snapshot.getLevel();
            ServerLevel world = server.getLevel(snapshot.getDimension());
            if (world == null) {
                world = (ServerLevel) player.level();
            }
            player.teleportTo(world, snapshot.getX(), snapshot.getY(), snapshot.getZ(),
                    Set.<Relative>of(), snapshot.getYaw(), snapshot.getPitch(), false);
        } else {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    @Override
    public void discardSnapshot(UUID uuid) {
        snapshots.remove(uuid);
        hotbarSlots.remove(uuid);
    }

    @Override
    public void clearSnapshots() {
        snapshots.clear();
        hotbarSlots.clear();
    }

    @Override
    public void hidePlayer(UUID viewerUuid, UUID hiddenUuid) {
        ServerPlayer viewer = player(viewerUuid);
        ServerPlayer hidden = player(hiddenUuid);
        if (viewer == null || hidden == null) return;
        if (!hiddenFromViewer.computeIfAbsent(viewerUuid, k -> ConcurrentHashMap.newKeySet()).add(hiddenUuid)) return;

        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(hidden.getUUID()));
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(hidden.getId()));
    }

    @Override
    public void showPlayer(UUID viewerUuid, UUID shownUuid) {
        ServerPlayer viewer = player(viewerUuid);
        ServerPlayer shown = player(shownUuid);
        if (viewer == null || shown == null) return;
        Set<UUID> hidden = hiddenFromViewer.get(viewerUuid);
        if (hidden == null || !hidden.remove(shownUuid)) return;

        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        GameProfile mojangProfile = shown.getGameProfile();
        List<TextureProperty> textureProperties = new ArrayList<>();
        for (Property prop : mojangProfile.properties().get("textures")) {
            textureProperties.add(new TextureProperty("textures", prop.value(), prop.signature()));
        }
        UserProfile profile = new UserProfile(shown.getUUID(), mojangProfile.name(), textureProperties);

        GameMode peGameMode = GameMode.values()[shown.gameMode.getGameModeForPlayer().ordinal()];

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, shown.connection.latency(), peGameMode,
                net.kyori.adventure.text.Component.text(shown.getName().getString()), null);

        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE),
                info));

        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerSpawnEntity(
                shown.getId(),
                Optional.of(shown.getUUID()),
                EntityTypes.PLAYER,
                new Vector3d(shown.getX(), shown.getY(), shown.getZ()),
                shown.getXRot(),
                shown.getYRot(),
                shown.getYRot(),
                0,
                Optional.of(new Vector3d(0, 0, 0))));
    }

    void forgetViewer(UUID uuid) {
        hiddenFromViewer.remove(uuid);
        for (Set<UUID> hidden : hiddenFromViewer.values()) {
            hidden.remove(uuid);
        }
    }

    void clearHidden() {
        hiddenFromViewer.clear();
    }

    @Override
    public void teleportToPlayer(UUID uuid, UUID targetUuid) {
        ServerPlayer player = player(uuid);
        ServerPlayer target = player(targetUuid);
        if (player == null || target == null) return;
        player.teleportTo((ServerLevel) target.level(), target.getX(), target.getY(), target.getZ(),
                Set.<Relative>of(), target.getYRot(), target.getXRot(), false);
    }

    @Override
    public void createScoreboard(UUID uuid, ScoreboardContent content) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                net.kyori.adventure.text.Component.text(content.getTitle()),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore()));
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerDisplayScoreboard(1, SCOREBOARD_OBJECTIVE));
        renderScores(peApi, player, content);
    }

    @Override
    public void updateScoreboard(UUID uuid, ScoreboardContent content) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        renderScores(peApi, player, content);
    }

    private void renderScores(PacketEventsAPI<?> peApi, ServerPlayer player, ScoreboardContent content) {
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                net.kyori.adventure.text.Component.text(content.getTitle()),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore()));

        Set<String> oldEntries = previousScoreEntries.getOrDefault(player.getUUID(), Collections.emptySet());
        Set<String> newEntries = new HashSet<>();
        for (ScoreboardContent.Line line : content.getLines()) {
            String entry = line.getText();
            newEntries.add(entry);
            peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerUpdateScore(
                    entry,
                    WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                    SCOREBOARD_OBJECTIVE,
                    line.getScore(),
                    null,
                    ScoreFormat.blankScore()));
        }
        for (String oldEntry : oldEntries) {
            if (!newEntries.contains(oldEntry)) {
                peApi.getPlayerManager().sendPacket(player,
                        new WrapperPlayServerResetScore(oldEntry, SCOREBOARD_OBJECTIVE));
            }
        }
        previousScoreEntries.put(player.getUUID(), newEntries);
    }

    @Override
    public void removeScoreboard(UUID uuid) {
        previousScoreEntries.remove(uuid);
        ServerPlayer player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                net.kyori.adventure.text.Component.empty(),
                null));
    }

    @Override
    public void discardScoreboard(UUID uuid) {
        previousScoreEntries.remove(uuid);
    }

    @Override
    public void clearScoreboards() {
        previousScoreEntries.clear();
    }

    @Override
    public void openTargetInventory(UUID viewerUuid, UUID targetUuid) {
        ServerPlayer viewer = player(viewerUuid);
        ServerPlayer target = player(targetUuid);
        if (viewer == null || target == null) return;

        SimpleContainer view = new SimpleContainer(TARGET_INVENTORY_SIZE);
        for (int i = 0; i < TARGET_INVENTORY_SIZE && i < target.getInventory().getContainerSize(); i++) {
            view.setItem(i, target.getInventory().getItem(i).copy());
        }
        viewer.openMenu(new SimpleMenuProvider(
                (syncId, playerInv, ignored) -> new ChestMenu(
                        MenuType.GENERIC_9x5, syncId, playerInv, view, 5),
                Component.literal(target.getName().getString() + "'s Inventory")));
    }

    @Override
    public void runPlayerCommand(UUID uuid, String command) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayer player = player(uuid);
        if (player == null) return;
        player.sendSystemMessage(Component.literal(message), false);
    }
}
