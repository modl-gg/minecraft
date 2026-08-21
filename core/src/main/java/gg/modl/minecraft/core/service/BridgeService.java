package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.query.BridgeBroadcaster;
import lombok.Setter;

@Setter
public class BridgeService {
    private volatile BridgeBroadcaster executor;
    private volatile LocalBridgeHandler localHandler;

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

    public boolean sendFreezePlayer(String targetUuid, String staffUuid) {
        LocalBridgeHandler handler = localHandler;
        if (handler != null) handler.onFreezePlayer(targetUuid, staffUuid);
        return broadcast(BridgeAction.FREEZE_PLAYER, targetUuid, staffUuid) > 0 || handler != null;
    }

    public boolean sendUnfreezePlayer(String targetUuid) {
        LocalBridgeHandler handler = localHandler;
        if (handler != null) handler.onUnfreezePlayer(targetUuid);
        return broadcast(BridgeAction.UNFREEZE_PLAYER, targetUuid) > 0 || handler != null;
    }

    public void sendTargetRequest(String staffUuid, String targetUuid) {
        if (localHandler != null) localHandler.onTargetRequest(staffUuid, targetUuid);
        broadcast(BridgeAction.TARGET_REQUEST, staffUuid, targetUuid);
    }

    private int broadcast(BridgeAction action, String... args) {
        BridgeBroadcaster broadcaster = executor;
        return broadcaster != null ? broadcaster.sendToAllBridges(action.wire(), args) : 0;
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
