package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import lombok.Setter;

/**
 * Lazy bridge to QueryStatWipeExecutor, set after plugin initialization.
 */
@Setter
public class BridgeService {
    private static final String CMD_STAFF_MODE_ENTER = "STAFF_MODE_ENTER";
    private static final String CMD_STAFF_MODE_EXIT = "STAFF_MODE_EXIT";
    private static final String CMD_VANISH_ENTER = "VANISH_ENTER";
    private static final String CMD_VANISH_EXIT = "VANISH_EXIT";
    private static final String CMD_FREEZE_PLAYER = "FREEZE_PLAYER";
    private static final String CMD_UNFREEZE_PLAYER = "UNFREEZE_PLAYER";
    private static final String CMD_TARGET_REQUEST = "TARGET_REQUEST";

    private volatile QueryStatWipeExecutor executor;

    public boolean isAvailable() {
        return executor != null;
    }

    public void sendStaffModeEnter(String staffUuid, String inGameName, String panelName) {
        broadcast(CMD_STAFF_MODE_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendStaffModeExit(String staffUuid, String inGameName, String panelName) {
        broadcast(CMD_STAFF_MODE_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendVanishEnter(String staffUuid, String inGameName, String panelName) {
        broadcast(CMD_VANISH_ENTER, staffUuid, inGameName, panelName);
    }

    public void sendVanishExit(String staffUuid, String inGameName, String panelName) {
        broadcast(CMD_VANISH_EXIT, staffUuid, inGameName, panelName);
    }

    public void sendFreezePlayer(String targetUuid, String staffUuid) {
        broadcast(CMD_FREEZE_PLAYER, targetUuid, staffUuid);
    }

    public void sendUnfreezePlayer(String targetUuid) {
        broadcast(CMD_UNFREEZE_PLAYER, targetUuid);
    }

    public void sendTargetRequest(String staffUuid, String targetUuid) {
        broadcast(CMD_TARGET_REQUEST, staffUuid, targetUuid);
    }

    private void broadcast(String command, String... args) {
        if (executor != null) executor.sendToAllBridges(command, args);
    }
}
