package gg.modl.minecraft.neoforge;

import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.neoforge.handler.NeoForgeFreezeHandler;
import gg.modl.minecraft.neoforge.handler.NeoForgeStaffModeHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class NeoForgeBridgeMessageHandler implements BridgeMessageHandler {
    private final MinecraftServer server;
    private final NeoForgeFreezeHandler freezeHandler;
    private final NeoForgeStaffModeHandler staffModeHandler;
    private final StatWipeHandler statWipeHandler;
    private final NeoForgeBridgeComponent bridgeComponent;

    public NeoForgeBridgeMessageHandler(MinecraftServer server, NeoForgeFreezeHandler freezeHandler,
                                         NeoForgeStaffModeHandler staffModeHandler,
                                         StatWipeHandler statWipeHandler,
                                         NeoForgeBridgeComponent bridgeComponent) {
        this.server = server;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.statWipeHandler = statWipeHandler;
        this.bridgeComponent = bridgeComponent;
    }

    @Override public void onFreeze(String targetUuid, String staffUuid) { server.execute(() -> freezeHandler.freeze(targetUuid, staffUuid)); }
    @Override public void onUnfreeze(String targetUuid) { server.execute(() -> freezeHandler.unfreeze(targetUuid)); }
    @Override public void onStaffModeEnter(String staffUuid, String staffName) { server.execute(() -> staffModeHandler.enterStaffMode(staffUuid)); }
    @Override public void onStaffModeExit(String staffUuid, String staffName) { server.execute(() -> staffModeHandler.exitStaffMode(staffUuid)); }
    @Override public void onVanishEnter(String staffUuid, String staffName) { server.execute(() -> staffModeHandler.vanishFromBridge(staffUuid)); }
    @Override public void onVanishExit(String staffUuid, String staffName) { server.execute(() -> staffModeHandler.unvanishFromBridge(staffUuid)); }

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
        server.execute(() -> statWipeHandler.execute(username, uuid, punishmentId));
    }

    @Override
    public void onCaptureReplay(String targetUuid, String targetName) {
        if (bridgeComponent.getBridgeClient() != null)
            bridgeComponent.getBridgeClient().sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
    }

    @Override public void onPanelUrl(String panelUrl) {}
}
