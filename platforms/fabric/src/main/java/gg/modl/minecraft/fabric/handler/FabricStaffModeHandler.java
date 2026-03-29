package gg.modl.minecraft.fabric.handler;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

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

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null) return;

        saveSnapshot(player);
        player.getInventory().clear();
        player.changeGameMode(GameMode.CREATIVE);

        if (staffModeConfig.isVanishOnEnable()) {
            vanish(player);
        }

        setupHotbar(player, staffModeConfig.getStaffHotbar());
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
            if (targetPlayer != null) {
                staffPlayer.teleport(targetPlayer.getServerWorld(),
                        targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        targetPlayer.getYaw(), targetPlayer.getPitch());
            }
        }
    }

    public void clearTarget(UUID staffUuid) {
        targetMap.remove(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(staffUuid);
        if (player != null) {
            setupHotbar(player, staffModeConfig.getStaffHotbar());
        }
    }

    public void vanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            vanish(player);
        }
    }

    public void unvanishFromBridge(String staffUuid) {
        UUID uuid = UUID.fromString(staffUuid);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            unvanish(player);
        }
    }

    private void vanish(ServerPlayerEntity staff) {
        vanished.add(staff.getUuid());
        // TODO: Use PacketEvents-Fabric to hide player from non-staff players
        // For now, just track the vanish state
    }

    private void unvanish(ServerPlayerEntity staff) {
        vanished.remove(staff.getUuid());
        // TODO: Use PacketEvents-Fabric to show player to all players
    }

    private void saveSnapshot(ServerPlayerEntity player) {
        snapshots.put(player.getUuid(), new PlayerSnapshot(
                player.getInventory().main.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
                player.getInventory().armor.stream().map(ItemStack::copy).toArray(ItemStack[]::new),
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
            for (int i = 0; i < snapshot.inventoryContents.length && i < player.getInventory().main.size(); i++) {
                player.getInventory().main.set(i, snapshot.inventoryContents[i]);
            }
            for (int i = 0; i < snapshot.armorContents.length && i < player.getInventory().armor.size(); i++) {
                player.getInventory().armor.set(i, snapshot.armorContents[i]);
            }
            player.changeGameMode(snapshot.gameMode);
            player.setHealth(Math.min(snapshot.health, player.getMaxHealth()));
            player.getHungerManager().setFoodLevel(snapshot.foodLevel);
            player.experienceProgress = snapshot.exp;
            player.experienceLevel = snapshot.level;
            player.teleport(player.getServerWorld(), snapshot.x, snapshot.y, snapshot.z,
                    snapshot.yaw, snapshot.pitch);
        } else {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
        }
    }

    private void setupHotbar(ServerPlayerEntity player, Map<Integer, StaffModeConfig.HotbarItem> hotbar) {
        player.getInventory().clear();
        hotbar.forEach((slot, hotbarItem) -> {
            if (slot >= 0 && slot <= 8) {
                ItemStack item = createItemStack(hotbarItem);
                player.getInventory().setStack(slot, item);
            }
        });
    }

    private ItemStack createItemStack(StaffModeConfig.HotbarItem hotbarItem) {
        String materialName = hotbarItem.getItem().replace("minecraft:", "");
        Identifier id = Identifier.of("minecraft", materialName);
        var itemEntry = Registries.ITEM.getOrEmpty(id);
        ItemStack stack = itemEntry.map(item -> new ItemStack(item, 1)).orElse(new ItemStack(Items.STONE));
        String displayName = localeManager.colorize(hotbarItem.getName());
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        return stack;
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        // Hide vanished staff from joining non-staff players
        // TODO: Implement with PacketEvents-Fabric
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
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
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
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
        final GameMode gameMode;
        final float health;
        final int foodLevel;
        final float exp;
        final int level;

        PlayerSnapshot(ItemStack[] inventoryContents, ItemStack[] armorContents,
                       double x, double y, double z, float yaw, float pitch,
                       GameMode gameMode, float health, int foodLevel, float exp, int level) {
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
