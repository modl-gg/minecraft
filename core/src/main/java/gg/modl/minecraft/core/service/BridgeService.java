package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import lombok.Setter;

@Setter
public class BridgeService {
    private static final String CMD_STAFF_MODE_ENTER = "STAFF_MODE_ENTER",
            CMD_STAFF_MODE_EXIT = "STAFF_MODE_EXIT",
            CMD_VANISH_ENTER = "VANISH_ENTER",
            CMD_VANISH_EXIT = "VANISH_EXIT",
            CMD_FREEZE_PLAYER = "FREEZE_PLAYER",
            CMD_UNFREEZE_PLAYER = "UNFREEZE_PLAYER",
            CMD_TARGET_REQUEST = "TARGET_REQUEST";

    private volatile QueryStatWipeExecutor executor;
    private volatile LocalBridgeHandler localHandler;

    public boolean isAvailable() {
        return executor != null || localHandler != null;
    }

    public void sendStaffModeEnter(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onStaffModeEnter(staffUuid);
        broadcast(CMD_STAFF_MODE_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendStaffModeExit(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onStaffModeExit(staffUuid);
        broadcast(CMD_STAFF_MODE_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendVanishEnter(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onVanishEnter(staffUuid);
        broadcast(CMD_VANISH_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendVanishExit(String staffUuid, String inGameName, String panelName) {
        if (localHandler != null) localHandler.onVanishExit(staffUuid);
        broadcast(CMD_VANISH_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendFreezePlayer(String targetUuid, String staffUuid) {
        if (localHandler != null) localHandler.onFreezePlayer(targetUuid, staffUuid);
        broadcast(CMD_FREEZE_PLAYER, targetUuid, staffUuid);
    }

    public void sendUnfreezePlayer(String targetUuid) {
        if (localHandler != null) localHandler.onUnfreezePlayer(targetUuid);
        broadcast(CMD_UNFREEZE_PLAYER, targetUuid);
    }

    public void sendTargetRequest(String staffUuid, String targetUuid) {
        if (localHandler != null) localHandler.onTargetRequest(staffUuid, targetUuid);
        broadcast(CMD_TARGET_REQUEST, staffUuid, targetUuid);
    }

    private void broadcast(String command, String... args) {
        if (executor != null) executor.sendToAllBridges(command, args);
    }

    /**
     * Handles bridge actions locally (standalone mode) without TCP round-trip.
     */
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
