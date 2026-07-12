package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.staffmode.StaffModeCore;
import gg.modl.minecraft.fabric.v26.ModlFabricModImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class FabricStaffModeHandler {
    private static final String SILENT_CONTAINER_PREFIX = "§8Viewing: ";
    private static final int VANISH_TARGET_CLEAR_INTERVAL_TICKS = 10;
    private static final double VANISH_TARGET_CLEAR_RADIUS = 48.0;
    private static final int MAX_FOOD_LEVEL = 20;

    private final MinecraftServer server;
    private final FabricStaffModeOps ops;
    private final StaffModeCore core;

    private int vanishClearTickCounter = 0;

    public FabricStaffModeHandler(MinecraftServer server, BridgeConfig bridgeConfig,
                                  FabricFreezeHandler freezeHandler,
                                  BridgeLocaleManager localeManager,
                                  StaffModeConfig staffModeConfig,
                                  BridgeScheduler scheduler) {
        this.server = server;
        this.ops = new FabricStaffModeOps(server, ModlFabricModImpl.LOGGER);
        this.core = new StaffModeCore(bridgeConfig, staffModeConfig, localeManager, scheduler,
                freezeHandler.getFreezeCore(), ops);
    }

    public void start() {
        core.start();
    }

    public void shutdown() {
        core.shutdown();
        ops.clearHidden();
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        core.setBridgeClient(bridgeClient);
    }

    public boolean isInStaffMode(UUID uuid) {
        return core.isInStaffMode(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return core.isVanished(uuid);
    }

    public void enterStaffMode(String staffUuid) {
        core.enterStaffMode(staffUuid);
    }

    public void exitStaffMode(String staffUuid) {
        core.exitStaffMode(staffUuid);
    }

    public void setTarget(String staffUuid, String targetUuid) {
        core.setTarget(staffUuid, targetUuid);
    }

    public void vanishFromBridge(String staffUuid) {
        core.vanishFromBridge(staffUuid);
    }

    public void unvanishFromBridge(String staffUuid) {
        core.unvanishFromBridge(staffUuid);
    }

    public void handleHotbarAction(UUID staffUuid, int heldSlot) {
        core.handleHotbarAction(staffUuid, heldSlot);
    }

    public void handleTargetSelect(UUID staffUuid, int heldSlot, UUID clickedUuid) {
        core.handleTargetSelect(staffUuid, heldSlot, clickedUuid);
    }

    public void onPlayerJoin(ServerPlayer player) {
        core.handlePlayerJoin(player.getUUID());
    }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        core.handlePlayerQuit(uuid);
        ops.forgetViewer(uuid);
    }

    public void onTick() {
        if (anyVanished() && (++vanishClearTickCounter % VANISH_TARGET_CLEAR_INTERVAL_TICKS == 0)) {
            clearVanishedMobTargets();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!core.isInStaffMode(uuid) || !ops.hasSnapshot(uuid)) continue;

            if (player.getFoodData().getFoodLevel() < MAX_FOOD_LEVEL) {
                player.getFoodData().setFoodLevel(MAX_FOOD_LEVEL);
            }
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).isEmpty() || ops.isProtectedSlot(uuid, i)) continue;
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean anyVanished() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (core.isVanished(player.getUUID())) return true;
        }
        return false;
    }

    private void clearVanishedMobTargets() {
        for (ServerPlayer vanishedPlayer : server.getPlayerList().getPlayers()) {
            if (!core.isVanished(vanishedPlayer.getUUID())) continue;
            ServerLevel level = (ServerLevel) vanishedPlayer.level();
            AABB box = vanishedPlayer.getBoundingBox().inflate(VANISH_TARGET_CLEAR_RADIUS);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == vanishedPlayer)) {
                mob.setTarget(null);
            }
        }
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
                (syncId, playerInv, ignored) -> {
                    MenuType<ChestMenu> menuType = switch (rows) {
                        case 1 -> MenuType.GENERIC_9x1;
                        case 2 -> MenuType.GENERIC_9x2;
                        case 3 -> MenuType.GENERIC_9x3;
                        case 4 -> MenuType.GENERIC_9x4;
                        case 5 -> MenuType.GENERIC_9x5;
                        default -> MenuType.GENERIC_9x6;
                    };
                    return new ChestMenu(menuType, syncId, playerInv, viewInventory, rows);
                },
                Component.literal(SILENT_CONTAINER_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ())));
    }
}
