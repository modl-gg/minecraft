package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class SpigotBridgeActions {
    private final StaffModeHandler staffModeHandler;
    private final FreezeHandler freezeHandler;

    public void enterStaffMode(String staffUuid) {
        staffModeHandler.enterStaffMode(staffUuid);
    }

    public void exitStaffMode(String staffUuid) {
        staffModeHandler.exitStaffMode(staffUuid);
    }

    public void enterVanish(String staffUuid) {
        staffModeHandler.vanishFromBridge(staffUuid);
    }

    public void exitVanish(String staffUuid) {
        staffModeHandler.unvanishFromBridge(staffUuid);
    }

    public void freeze(String targetUuid, String staffUuid) {
        freezeHandler.freeze(targetUuid, staffUuid);
    }

    public void unfreeze(String targetUuid) {
        freezeHandler.unfreeze(targetUuid);
    }

    public void setTarget(String staffUuid, String targetUuid) {
        staffModeHandler.setTarget(staffUuid, targetUuid);
    }
}
