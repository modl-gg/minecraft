package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;

public class SpigotStandaloneLocalBridgeHandler implements BridgeService.LocalBridgeHandler {
    private final StaffModeHandler staffModeHandler;
    private final FreezeHandler freezeHandler;

    public SpigotStandaloneLocalBridgeHandler(StaffModeHandler staffModeHandler, FreezeHandler freezeHandler) {
        this.staffModeHandler = staffModeHandler;
        this.freezeHandler = freezeHandler;
    }

    @Override
    public void onStaffModeEnter(String staffUuid) {
        staffModeHandler.enterStaffMode(staffUuid);
    }

    @Override
    public void onStaffModeExit(String staffUuid) {
        staffModeHandler.exitStaffMode(staffUuid);
    }

    @Override
    public void onVanishEnter(String staffUuid) {
        staffModeHandler.vanishFromBridge(staffUuid);
    }

    @Override
    public void onVanishExit(String staffUuid) {
        staffModeHandler.unvanishFromBridge(staffUuid);
    }

    @Override
    public void onFreezePlayer(String targetUuid, String staffUuid) {
        freezeHandler.freeze(targetUuid, staffUuid);
    }

    @Override
    public void onUnfreezePlayer(String targetUuid) {
        freezeHandler.unfreeze(targetUuid);
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        staffModeHandler.setTarget(staffUuid, targetUuid);
    }
}
