package gg.modl.minecraft.fabric.v1_21_11;

import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.fabric.v1_21_11.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v1_21_11.handler.FabricStaffModeHandler;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;

@RequiredArgsConstructor
public class FabricLocalBridgeHandler implements BridgeService.LocalBridgeHandler {
    private final MinecraftServer server;
    private final FabricStaffModeHandler staffModeHandler;
    private final FabricFreezeHandler freezeHandler;

    @Override
    public void onStaffModeEnter(String staffUuid) {
        server.execute(() -> staffModeHandler.enterStaffMode(staffUuid));
    }

    @Override
    public void onStaffModeExit(String staffUuid) {
        server.execute(() -> staffModeHandler.exitStaffMode(staffUuid));
    }

    @Override
    public void onVanishEnter(String staffUuid) {
        server.execute(() -> staffModeHandler.vanishFromBridge(staffUuid));
    }

    @Override
    public void onVanishExit(String staffUuid) {
        server.execute(() -> staffModeHandler.unvanishFromBridge(staffUuid));
    }

    @Override
    public void onFreezePlayer(String targetUuid, String staffUuid) {
        server.execute(() -> freezeHandler.freeze(targetUuid, staffUuid));
    }

    @Override
    public void onUnfreezePlayer(String targetUuid) {
        server.execute(() -> freezeHandler.unfreeze(targetUuid));
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        server.execute(() -> staffModeHandler.setTarget(staffUuid, targetUuid));
    }
}
