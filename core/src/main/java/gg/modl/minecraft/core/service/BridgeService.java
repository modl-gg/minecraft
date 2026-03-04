package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.query.QueryStatWipeExecutor;

/**
 * Provides bridge communication for commands and services.
 * Holds a lazy reference to the QueryStatWipeExecutor which is set after plugin initialization.
 */
public class BridgeService {

    private volatile QueryStatWipeExecutor executor;

    public void setExecutor(QueryStatWipeExecutor executor) {
        this.executor = executor;
    }

    public boolean isAvailable() {
        return executor != null;
    }

    public void sendStaffModeEnter(String staffUuid, String inGameName, String panelName) {
        if (executor != null) {
            executor.sendToAllBridges("STAFF_MODE_ENTER", staffUuid, inGameName, panelName);
        }
    }

    public void sendStaffModeExit(String staffUuid, String inGameName, String panelName) {
        if (executor != null) {
            executor.sendToAllBridges("STAFF_MODE_EXIT", staffUuid, inGameName, panelName);
        }
    }

    public void sendVanishEnter(String staffUuid, String inGameName, String panelName) {
        if (executor != null) {
            executor.sendToAllBridges("VANISH_ENTER", staffUuid, inGameName, panelName);
        }
    }

    public void sendVanishExit(String staffUuid, String inGameName, String panelName) {
        if (executor != null) {
            executor.sendToAllBridges("VANISH_EXIT", staffUuid, inGameName, panelName);
        }
    }

    public void sendFreezePlayer(String targetUuid, String staffUuid) {
        if (executor != null) {
            executor.sendToAllBridges("FREEZE_PLAYER", targetUuid, staffUuid);
        }
    }

    public void sendUnfreezePlayer(String targetUuid) {
        if (executor != null) {
            executor.sendToAllBridges("UNFREEZE_PLAYER", targetUuid);
        }
    }

    public void sendFreezeLogout(String playerUuid, String playerName) {
        if (executor != null) {
            executor.sendToAllBridges("FREEZE_LOGOUT", playerUuid, playerName);
        }
    }

    public void sendTargetRequest(String staffUuid, String targetUuid) {
        if (executor != null) {
            executor.sendToAllBridges("TARGET_REQUEST", staffUuid, targetUuid);
        }
    }
}
