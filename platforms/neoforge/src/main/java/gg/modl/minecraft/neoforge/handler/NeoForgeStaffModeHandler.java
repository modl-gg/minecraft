package gg.modl.minecraft.neoforge.handler;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NeoForgeStaffModeHandler {
    private final MinecraftServer server;
    private final BridgeConfig bridgeConfig;
    private final NeoForgeFreezeHandler freezeHandler;
    private final BridgeLocaleManager localeManager;
    private final StaffModeConfig staffModeConfig;
    @Setter private BridgeQueryClient bridgeClient;

    private final Set<UUID> staffModeActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> targetMap = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();

    public NeoForgeStaffModeHandler(MinecraftServer server, BridgeConfig bridgeConfig,
                                     NeoForgeFreezeHandler freezeHandler,
                                     BridgeLocaleManager localeManager,
                                     StaffModeConfig staffModeConfig) {
        this.server = server;
        this.bridgeConfig = bridgeConfig;
        this.freezeHandler = freezeHandler;
        this.localeManager = localeManager;
        this.staffModeConfig = staffModeConfig;
    }

    public boolean isInStaffMode(UUID uuid) { return staffModeActive.contains(uuid); }

    public void enterStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.add(uuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        saveSnapshot(player);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);
        if (staffModeConfig.isVanishOnEnable()) vanished.add(uuid);
        setupHotbar(player, staffModeConfig.getStaffHotbar());
    }

    public void exitStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.remove(uuid);
        targetMap.remove(uuid);
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) { snapshots.remove(uuid); vanished.remove(uuid); return; }
        vanished.remove(uuid);
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
            if (targetPlayer != null) {
                staffPlayer.teleportTo(targetPlayer.serverLevel(),
                        targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        java.util.Set.of(), targetPlayer.getYRot(), targetPlayer.getXRot(), false);
            }
        }
    }

    public void clearTarget(UUID staffUuid) { targetMap.remove(staffUuid); }
    public void vanishFromBridge(String staffUuid) { vanished.add(UUID.fromString(staffUuid)); }
    public void unvanishFromBridge(String staffUuid) { vanished.remove(UUID.fromString(staffUuid)); }

    private void saveSnapshot(ServerPlayer player) {
        List<ItemStack> mainItems = new ArrayList<>();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            mainItems.add(player.getInventory().items.get(i).copy());
        }
        List<ItemStack> armorItems = new ArrayList<>();
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            armorItems.add(player.getInventory().armor.get(i).copy());
        }
        snapshots.put(player.getUUID(), new PlayerSnapshot(
                mainItems, armorItems,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(), player.getHealth(),
                player.getFoodData().getFoodLevel(), player.experienceProgress, player.experienceLevel
        ));
    }

    private void restoreSnapshot(ServerPlayer player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUUID());
        if (snapshot != null) {
            player.getInventory().clearContent();
            for (int i = 0; i < snapshot.inv.size() && i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, snapshot.inv.get(i));
            }
            for (int i = 0; i < snapshot.armor.size() && i < player.getInventory().armor.size(); i++) {
                player.getInventory().armor.set(i, snapshot.armor.get(i));
            }
            player.setGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleportTo(player.serverLevel(), snapshot.x, snapshot.y, snapshot.z,
                    java.util.Set.of(), snapshot.yaw, snapshot.pitch, false);
        } else {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private void setupHotbar(ServerPlayer player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clearContent();
        hotbar.forEach((slot, item) -> {
            if (slot >= 0 && slot <= 8) player.getInventory().setItem(slot, createItemStack(item));
        });
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem) {
        String materialName = hotbarItem.getItem().replace("minecraft:", "");
        ResourceLocation id = ResourceLocation.withDefaultNamespace(materialName);
        var item = BuiltInRegistries.ITEM.getOptional(id);
        ItemStack stack = item.map(i -> new ItemStack(i, 1)).orElse(new ItemStack(Items.STONE));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(localeManager.colorize(hotbarItem.getName())));
        return stack;
    }

    public void onPlayerJoin(ServerPlayer player) { /* TODO: vanish packets */ }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (staffModeActive.remove(uuid)) restoreSnapshot(player);
        targetMap.remove(uuid);
        vanished.remove(uuid);
        snapshots.remove(uuid);
    }

    public void shutdown() {
        for (UUID uuid : staffModeActive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) restoreSnapshot(player);
        }
        staffModeActive.clear();
    }

    private static class PlayerSnapshot {
        final List<ItemStack> inv, armor;
        final double x, y, z;
        final float yaw, pitch, health, exp;
        final GameType gameMode;
        final int foodLevel, level;
        PlayerSnapshot(List<ItemStack> inv, List<ItemStack> armor, double x, double y, double z,
                       float yaw, float pitch, GameType gm, float hp, int food, float exp, int lvl) {
            this.inv = inv; this.armor = armor; this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch; this.gameMode = gm; this.health = hp;
            this.foodLevel = food; this.exp = exp; this.level = lvl;
        }
    }
}
