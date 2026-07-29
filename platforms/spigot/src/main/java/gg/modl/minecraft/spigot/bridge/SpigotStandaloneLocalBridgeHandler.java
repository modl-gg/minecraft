package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.core.service.BridgeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SpigotStandaloneLocalBridgeHandler implements BridgeService.LocalBridgeHandler {
    private final SpigotBridgeActions actions;

    @Override
    public void onStaffModeEnter(String staffUuid) {
        actions.enterStaffMode(staffUuid);
    }

    @Override
    public void onStaffModeExit(String staffUuid) {
        actions.exitStaffMode(staffUuid);
    }

    @Override
    public void onVanishEnter(String staffUuid) {
        actions.enterVanish(staffUuid);
    }

    @Override
    public void onVanishExit(String staffUuid) {
        actions.exitVanish(staffUuid);
    }

    @Override
    public void onFreezePlayer(String targetUuid, String staffUuid) {
        actions.freeze(targetUuid, staffUuid);
    }

    @Override
    public void onUnfreezePlayer(String targetUuid) {
        actions.unfreeze(targetUuid);
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        actions.setTarget(staffUuid, targetUuid);
    }
}
