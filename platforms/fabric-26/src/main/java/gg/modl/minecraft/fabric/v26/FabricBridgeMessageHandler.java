package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.fabric.v26.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v26.handler.FabricStaffModeHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FabricBridgeMessageHandler implements BridgeMessageHandler {
    private final MinecraftServer server;
    private final FabricFreezeHandler freezeHandler;
    private final FabricStaffModeHandler staffModeHandler;
    private final StatWipeHandler statWipeHandler;
    private final FabricBridgeComponent bridgeComponent;

    public FabricBridgeMessageHandler(MinecraftServer server,
                                       FabricFreezeHandler freezeHandler,
                                       FabricStaffModeHandler staffModeHandler,
                                       StatWipeHandler statWipeHandler,
                                       FabricBridgeComponent bridgeComponent) {
        this.server = server;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.statWipeHandler = statWipeHandler;
        this.bridgeComponent = bridgeComponent;
    }

    @Override
    public void onFreeze(String targetUuid, String staffUuid) {
        server.execute(() -> freezeHandler.freeze(targetUuid, staffUuid));
    }

    @Override
    public void onUnfreeze(String targetUuid) {
        server.execute(() -> freezeHandler.unfreeze(targetUuid));
    }

    @Override
    public void onStaffModeEnter(String staffUuid, String staffName) {
        server.execute(() -> staffModeHandler.enterStaffMode(staffUuid));
    }

    @Override
    public void onStaffModeExit(String staffUuid, String staffName) {
        server.execute(() -> staffModeHandler.exitStaffMode(staffUuid));
    }

    @Override
    public void onVanishEnter(String staffUuid, String staffName) {
        server.execute(() -> staffModeHandler.vanishFromBridge(staffUuid));
    }

    @Override
    public void onVanishExit(String staffUuid, String staffName) {
        server.execute(() -> staffModeHandler.unvanishFromBridge(staffUuid));
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(targetUuid));
            if (target == null) return;

            bridgeComponent.getBridgeClient().sendMessage("TARGET_RESPONSE", staffUuid, targetUuid,
                    bridgeComponent.getBridgeConfig().getServerName());
            staffModeHandler.setTarget(staffUuid, targetUuid);
        });
    }

    @Override
    public void onStatWipe(String username, String uuid, String punishmentId) {
        server.execute(() -> {
            ModlFabricModImpl.LOGGER.info("[bridge] Processing stat-wipe for {} (punishment: {})", username, punishmentId);
            boolean success = statWipeHandler.execute(username, uuid, punishmentId);
            ModlFabricModImpl.LOGGER.info("[bridge] Stat-wipe for {} {} (punishment: {})",
                    username, success ? "succeeded" : "failed", punishmentId);
        });
    }

    @Override
    public void onCaptureReplay(String targetUuid, String targetName) {
        if (bridgeComponent.getBridgeClient() != null) {
            bridgeComponent.getBridgeClient().sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
        }
    }

    @Override
    public void onPanelUrl(String panelUrl) {
        // Handled by BridgeQueryClient
    }
}
