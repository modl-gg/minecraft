package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.query.BridgeBroadcaster;
import lombok.Setter;

@Setter
public class BridgeService {
    private volatile BridgeBroadcaster executor;
    private volatile LocalBridgeHandler localHandler;

    public boolean isAvailable() {
        return executor != null || localHandler != null;
    }

    public void sendStaffModeEnter(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onStaffModeEnter(staffUuid);
        broadcast(BridgeAction.STAFF_MODE_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendStaffModeExit(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onStaffModeExit(staffUuid);
        broadcast(BridgeAction.STAFF_MODE_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendVanishEnter(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onVanishEnter(staffUuid);
        broadcast(BridgeAction.VANISH_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendVanishExit(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onVanishExit(staffUuid);
        broadcast(BridgeAction.VANISH_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendFreezePlayer(String targetUuid, String staffUuid) {
        if (localHandler != null) localHandler.onFreezePlayer(targetUuid, staffUuid);
        broadcast(BridgeAction.FREEZE_PLAYER, targetUuid, staffUuid);
    }

    public void sendUnfreezePlayer(String targetUuid) {
        if (localHandler != null) localHandler.onUnfreezePlayer(targetUuid);
        broadcast(BridgeAction.UNFREEZE_PLAYER, targetUuid);
    }

    public void sendTargetRequest(String staffUuid, String targetUuid) {
        if (localHandler != null) localHandler.onTargetRequest(staffUuid, targetUuid);
        broadcast(BridgeAction.TARGET_REQUEST, staffUuid, targetUuid);
    }

    private void broadcast(BridgeAction action, String... args) {
        if (executor != null) executor.sendToAllBridges(action.wire(), args);
    }

    public interface LocalBridgeHandler {
        void onStaffModeEnter(String staffUuid);
        void onStaffModeExit(String staffUuid);
        void onVanishEnter(String staffUuid);
        void onVanishExit(String staffUuid);
        void onFreezePlayer(String targetUuid, String staffUuid);
        void onUnfreezePlayer(String targetUuid);
        void onTargetRequest(String staffUuid, String targetUuid);
    }
}
