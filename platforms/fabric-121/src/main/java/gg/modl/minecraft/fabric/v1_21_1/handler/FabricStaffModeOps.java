package gg.modl.minecraft.fabric.v1_21_1.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
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
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import gg.modl.minecraft.bridge.staffmode.ScoreboardContent;
import gg.modl.minecraft.bridge.staffmode.StaffGameMode;
import gg.modl.minecraft.bridge.staffmode.StaffModeOps;
import net.kyori.adventure.text.Component;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
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

    private ServerPlayerEntity player(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return player(uuid) != null;
    }

    @Override
    public String playerName(UUID uuid) {
        ServerPlayerEntity player = player(uuid);
        return player != null ? player.getName().getString() : null;
    }

    @Override
    public Set<UUID> onlinePlayerUuids() {
        return server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getUuid).collect(Collectors.toSet());
    }

    @Override
    public int onlinePlayerCount() {
        return server.getPlayerManager().getPlayerList().size();
    }

    @Override
    public int maxPlayerCount() {
        return server.getMaxPlayerCount();
    }

    @Override
    public int playerPing(UUID uuid) {
        ServerPlayerEntity player = player(uuid);
        return player != null ? player.networkHandler.getLatency() : -1;
    }

    @Override
    public double playerHealth(UUID uuid) {
        ServerPlayerEntity player = player(uuid);
        return player != null ? player.getHealth() : 0.0;
    }

    @Override
    public void clearInventory(UUID uuid) {
        hotbarSlots.remove(uuid);
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        player.getInventory().clear();
        player.getInventory().offHand.clear();
    }

    boolean isProtectedSlot(UUID uuid, int slot) {
        Set<Integer> slots = hotbarSlots.get(uuid);
        return slots != null && slots.contains(slot);
    }

    @Override
    public void clearArmor(UUID uuid) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        player.getInventory().armor.clear();
    }

    @Override
    public void setGameMode(UUID uuid, StaffGameMode mode) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        player.changeGameMode(mode == StaffGameMode.CREATIVE ? GameMode.CREATIVE : GameMode.SURVIVAL);
    }

    @Override
    public void setHotbarSlot(UUID uuid, int slot, String materialId, String displayName, List<String> lore) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        hotbarSlots.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(slot);
        player.getInventory().setStack(slot, buildItem(materialId, displayName, lore));
    }

    private ItemStack buildItem(String materialId, String displayName, List<String> lore) {
        String materialName = materialId.replace("minecraft:", "");
        Identifier id = Identifier.of("minecraft", materialName);
        Item item;
        if (Registries.ITEM.containsId(id)) {
            item = Registries.ITEM.get(id);
        } else {
            if (warnedItemIds.add(materialId)) {
                logger.warn("[staff-mode] Unknown hotbar item id '{}', using STONE", materialId);
            }
            item = Items.STONE;
        }
        ItemStack stack = new ItemStack(item, 1);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(
                    lore.stream().<Text>map(Text::literal).toList()));
        }
        return stack;
    }

    @Override
    public boolean hasSnapshot(UUID uuid) {
        return snapshots.containsKey(uuid);
    }

    @Override
    public void saveSnapshot(UUID uuid) {
        ServerPlayerEntity player = player(uuid);
        if (player == null || snapshots.containsKey(uuid)) return;
        snapshots.put(uuid, new PlayerSnapshot(
                player.getInventory().main.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
                player.getInventory().armor.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
                player.getInventory().offHand.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                player.interactionManager.getGameMode(),
                player.getHealth(),
                player.getHungerManager().getFoodLevel(),
                player.experienceProgress,
                player.experienceLevel));
    }

    @Override
    public void restoreSnapshot(UUID uuid) {
        hotbarSlots.remove(uuid);
        ServerPlayerEntity player = player(uuid);
        if (player == null) {
            snapshots.remove(uuid);
            return;
        }
        PlayerSnapshot snapshot = snapshots.remove(uuid);
        if (snapshot != null) {
            player.getInventory().clear();
            for (int i = 0; i < snapshot.inventoryContents.length && i < player.getInventory().main.size(); i++) {
                player.getInventory().main.set(i, snapshot.inventoryContents[i].copy());
            }
            for (int i = 0; i < snapshot.armorContents.length && i < player.getInventory().armor.size(); i++) {
                player.getInventory().armor.set(i, snapshot.armorContents[i].copy());
            }
            for (int i = 0; i < snapshot.offHandContents.length && i < player.getInventory().offHand.size(); i++) {
                player.getInventory().offHand.set(i, snapshot.offHandContents[i].copy());
            }
            player.changeGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getHungerManager().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleport(player.getServerWorld(), snapshot.x, snapshot.y, snapshot.z,
                    Set.of(), snapshot.yaw, snapshot.pitch);
        } else {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
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
        ServerPlayerEntity viewer = player(viewerUuid);
        ServerPlayerEntity hidden = player(hiddenUuid);
        if (viewer == null || hidden == null) return;
        if (!hiddenFromViewer.computeIfAbsent(viewerUuid, k -> ConcurrentHashMap.newKeySet()).add(hiddenUuid)) return;

        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoRemove(hidden.getUuid()));
        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(hidden.getId()));
    }

    @Override
    public void showPlayer(UUID viewerUuid, UUID shownUuid) {
        ServerPlayerEntity viewer = player(viewerUuid);
        ServerPlayerEntity shown = player(shownUuid);
        if (viewer == null || shown == null) return;
        Set<UUID> hidden = hiddenFromViewer.get(viewerUuid);
        if (hidden == null || !hidden.remove(shownUuid)) return;

        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        GameProfile mojangProfile = shown.getGameProfile();
        List<TextureProperty> textureProperties = new ArrayList<>();
        for (Property prop : mojangProfile.getProperties().get("textures")) {
            textureProperties.add(new TextureProperty("textures", prop.value(), prop.signature()));
        }
        UserProfile profile = new UserProfile(shown.getUuid(), mojangProfile.getName(), textureProperties);

        com.github.retrooper.packetevents.protocol.player.GameMode peGameMode =
                com.github.retrooper.packetevents.protocol.player.GameMode.values()[shown.interactionManager.getGameMode().ordinal()];

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, shown.networkHandler.getLatency(), peGameMode,
                Component.text(shown.getName().getString()), null);

        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE),
                info));

        peApi.getPlayerManager().sendPacket(viewer, new WrapperPlayServerSpawnEntity(
                shown.getId(),
                Optional.of(shown.getUuid()),
                EntityTypes.PLAYER,
                new Vector3d(shown.getX(), shown.getY(), shown.getZ()),
                shown.getPitch(),
                shown.getYaw(),
                shown.getYaw(),
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
        ServerPlayerEntity player = player(uuid);
        ServerPlayerEntity target = player(targetUuid);
        if (player == null || target == null) return;
        player.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(),
                Set.of(), target.getYaw(), target.getPitch());
    }

    @Override
    public void createScoreboard(UUID uuid, ScoreboardContent content) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;

        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                Component.text(content.getTitle()),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore()));
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerDisplayScoreboard(1, SCOREBOARD_OBJECTIVE));
        renderScores(peApi, player, content);
    }

    @Override
    public void updateScoreboard(UUID uuid, ScoreboardContent content) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        renderScores(peApi, player, content);
    }

    private void renderScores(PacketEventsAPI<?> peApi, ServerPlayerEntity player, ScoreboardContent content) {
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE,
                Component.text(content.getTitle()),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER,
                ScoreFormat.blankScore()));

        Set<String> oldEntries = previousScoreEntries.getOrDefault(player.getUuid(), Collections.emptySet());
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
        previousScoreEntries.put(player.getUuid(), newEntries);
    }

    @Override
    public void removeScoreboard(UUID uuid) {
        previousScoreEntries.remove(uuid);
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        PacketEventsAPI<?> peApi = PacketEvents.getAPI();
        if (peApi == null) return;
        peApi.getPlayerManager().sendPacket(player, new WrapperPlayServerScoreboardObjective(
                SCOREBOARD_OBJECTIVE,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                Component.empty(),
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
        ServerPlayerEntity viewer = player(viewerUuid);
        ServerPlayerEntity target = player(targetUuid);
        if (viewer == null || target == null) return;

        SimpleInventory view = new SimpleInventory(TARGET_INVENTORY_SIZE);
        for (int i = 0; i < TARGET_INVENTORY_SIZE && i < target.getInventory().size(); i++) {
            view.setStack(i, target.getInventory().getStack(i).copy());
        }
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new GenericContainerScreenHandler(
                        ScreenHandlerType.GENERIC_9X5, syncId, playerInv, view, 5),
                Text.literal(target.getName().getString() + "'s Inventory")));
    }

    @Override
    public void runPlayerCommand(UUID uuid, String command) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        server.getCommandManager().executeWithPrefix(player.getCommandSource(), command);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) return;
        player.sendMessage(Text.literal(message), false);
    }
}
