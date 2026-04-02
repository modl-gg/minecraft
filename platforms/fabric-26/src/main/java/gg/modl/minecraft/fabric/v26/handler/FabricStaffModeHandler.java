package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.core.component.DataComponents;
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

import static gg.modl.minecraft.core.util.Java8Collections.*;

public class FabricStaffModeHandler {
    private final MinecraftServer server;
    private final BridgeConfig bridgeConfig;
    private final FabricFreezeHandler freezeHandler;
    private final BridgeLocaleManager localeManager;
    private final StaffModeConfig staffModeConfig;
    @Setter private BridgeQueryClient bridgeClient;

    private final Set<UUID> staffModeActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> targetMap = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();

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

    public void enterStaffMode(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        staffModeActive.add(uuid);

        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;

        saveSnapshot(player);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);

        if (staffModeConfig.isVanishOnEnable()) {
            vanish(player);
        }

        setupHotbar(player, staffModeConfig.getStaffHotbar());
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
            if (targetPlayer != null) {
                staffPlayer.teleportTo(targetPlayer.serverLevel(),
                        targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        Set.of(), targetPlayer.getYRot(), targetPlayer.getXRot(), false);
            }
        }
    }

    public void clearTarget(UUID staffUuid) {
        targetMap.remove(staffUuid);
        ServerPlayer player = server.getPlayerList().getPlayer(staffUuid);
        if (player != null) {
            setupHotbar(player, staffModeConfig.getStaffHotbar());
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
        // TODO: Use PacketEvents-Fabric to hide player from non-staff players
    }

    private void unvanish(ServerPlayer staff) {
        vanished.remove(staff.getUUID());
        // TODO: Use PacketEvents-Fabric to show player to all players
    }

    private void saveSnapshot(ServerPlayer player) {
        snapshots.put(player.getUUID(), new PlayerSnapshot(
                player.getInventory().items.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
                player.getInventory().armor.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
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
            for (int i = 0; i < snapshot.inventoryContents.length && i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, snapshot.inventoryContents[i]);
            }
            for (int i = 0; i < snapshot.armorContents.length && i < player.getInventory().armor.size(); i++) {
                player.getInventory().armor.set(i, snapshot.armorContents[i]);
            }
            player.setGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleportTo(player.serverLevel(), snapshot.x, snapshot.y, snapshot.z,
                    Set.of(), snapshot.yaw, snapshot.pitch, false);
        } else {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private void setupHotbar(ServerPlayer player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clearContent();
        hotbar.forEach((slot, hotbarItem) -> {
            if (slot >= 0 && slot <= 8) {
                ItemStack item = createItemStack(hotbarItem);
                player.getInventory().setItem(slot, item);
            }
        });
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem) {
        String materialName = hotbarItem.getItem().replace("minecraft:", "");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", materialName);
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.getValue(id)
                : Items.STONE;
        ItemStack stack = new ItemStack(item, 1);
        String displayName = localeManager.colorize(hotbarItem.getName());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        return stack;
    }

    public void onPlayerJoin(ServerPlayer player) {
        // TODO: Implement vanish visibility with PacketEvents-Fabric
    }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (staffModeActive.remove(uuid)) {
            unvanish(player);
            restoreSnapshot(player);
        }
        targetMap.remove(uuid);
        vanished.remove(uuid);
        snapshots.remove(uuid);
    }

    public void shutdown() {
        for (UUID uuid : staffModeActive) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                restoreSnapshot(player);
            }
        }
        staffModeActive.clear();
    }

    private static class PlayerSnapshot {
        final ItemStack[] inventoryContents;
        final ItemStack[] armorContents;
        final double x, y, z;
        final float yaw, pitch;
        final GameType gameMode;
        final float health;
        final int foodLevel;
        final float exp;
        final int level;

        PlayerSnapshot(ItemStack[] inventoryContents, ItemStack[] armorContents,
                       double x, double y, double z, float yaw, float pitch,
                       GameType gameMode, float health, int foodLevel, float exp, int level) {
            this.inventoryContents = inventoryContents;
            this.armorContents = armorContents;
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
}
