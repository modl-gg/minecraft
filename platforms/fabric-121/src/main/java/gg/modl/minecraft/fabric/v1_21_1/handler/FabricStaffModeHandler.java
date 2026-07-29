package gg.modl.minecraft.fabric.v1_21_1.handler;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.staffmode.StaffModeCore;
import gg.modl.minecraft.fabric.v1_21_1.ModlFabricModImpl;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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

    public void onPlayerJoin(ServerPlayerEntity player) {
        core.handlePlayerJoin(player.getUuid());
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        core.handlePlayerQuit(uuid);
        ops.forgetViewer(uuid);
    }

    public void onTick() {
        if (anyVanished() && (++vanishClearTickCounter % VANISH_TARGET_CLEAR_INTERVAL_TICKS == 0)) {
            clearVanishedMobTargets();
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (!core.isInStaffMode(uuid) || !ops.hasSnapshot(uuid)) continue;

            if (player.getHungerManager().getFoodLevel() < MAX_FOOD_LEVEL) {
                player.getHungerManager().setFoodLevel(MAX_FOOD_LEVEL);
            }
            if (player.getHealth() < player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (player.getInventory().getStack(i).isEmpty() || ops.isProtectedSlot(uuid, i)) continue;
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean anyVanished() {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (core.isVanished(player.getUuid())) return true;
        }
        return false;
    }

    private void clearVanishedMobTargets() {
        for (ServerPlayerEntity vanishedPlayer : server.getPlayerManager().getPlayerList()) {
            if (!core.isVanished(vanishedPlayer.getUuid())) continue;
            ServerWorld world = vanishedPlayer.getServerWorld();
            Box box = vanishedPlayer.getBoundingBox().expand(VANISH_TARGET_CLEAR_RADIUS);
            for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, m -> m.getTarget() == vanishedPlayer)) {
                mob.setTarget(null);
            }
        }
    }

    public void openSilentContainer(ServerPlayerEntity player, Inventory container, BlockPos pos) {
        int size = container.size();
        int rows = Math.min(6, Math.max(1, (size + 8) / 9));
        int guiSize = rows * 9;

        SimpleInventory viewInventory = new SimpleInventory(guiSize);
        for (int i = 0; i < size && i < guiSize; i++) {
            viewInventory.setStack(i, container.getStack(i).copy());
        }

        ScreenHandlerType<?> handlerType = switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new GenericContainerScreenHandler(
                        handlerType, syncId, playerInv, viewInventory, rows),
                Text.literal(SILENT_CONTAINER_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ())));
    }
}
