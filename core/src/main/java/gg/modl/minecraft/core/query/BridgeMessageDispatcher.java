package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;

import java.io.DataInputStream;
import java.util.Map;
import java.util.UUID;
import gg.modl.minecraft.core.util.PluginLogger;

public class BridgeMessageDispatcher {
    private final Platform platform;
    private final LocaleManager localeManager;
    private final FreezeService freezeService;
    private final StaffModeService staffModeService;
    private final VanishService vanishService;
    private final PluginLogger logger;

    public BridgeMessageDispatcher(Platform platform, LocaleManager localeManager,
                                    FreezeService freezeService, StaffModeService staffModeService,
                                    VanishService vanishService, PluginLogger logger) {
        this.platform = platform;
        this.localeManager = localeManager;
        this.freezeService = freezeService;
        this.staffModeService = staffModeService;
        this.vanishService = vanishService;
        this.logger = logger;
    }

    public void dispatch(String action, DataInputStream data) {
        try {
            switch (action) {
                case "FREEZE_PLAYER" -> handleFreezePlayer(data);
                case "UNFREEZE_PLAYER" -> handleUnfreezePlayer(data);
                case "FREEZE_LOGOUT" -> handleFreezeLogout(data);
                case "STAFF_MODE_ENTER" -> handleStaffModeEnter(data);
                case "STAFF_MODE_EXIT" -> handleStaffModeExit(data);
                case "VANISH_ENTER" -> handleVanishEnter(data);
                case "VANISH_EXIT" -> handleVanishExit(data);
                case "TARGET_RESPONSE" -> handleTargetResponse(data);
                case "OPEN_STAFF_MENU" -> handleOpenStaffMenu(data);
                case "OPEN_INSPECT_MENU" -> handleOpenInspectMenu(data);
                case "PROXY_CMD" -> handleProxyCmd(data);
                default -> logger.debug("[bridge] Unknown action: " + action);
            }
        } catch (Exception e) {
            logger.warning("[bridge] Error handling " + action + ": " + e.getMessage());
        }
    }

    private void handleFreezePlayer(DataInputStream data) throws Exception {
        UUID targetUuid = UUID.fromString(data.readUTF());
        UUID staffUuid = UUID.fromString(data.readUTF());
        freezeService.freeze(targetUuid, staffUuid);
        platform.sendMessage(targetUuid, localeManager.getMessage("freeze.frozen_message"));
        logger.info("[bridge] Freeze applied to " + targetUuid + " by " + staffUuid);
    }

    private void handleUnfreezePlayer(DataInputStream data) throws Exception {
        UUID targetUuid = UUID.fromString(data.readUTF());
        freezeService.unfreeze(targetUuid);
        platform.sendMessage(targetUuid, localeManager.getMessage("freeze.unfrozen"));
        logger.info("[bridge] Unfreeze applied to " + targetUuid);
    }

    private void handleFreezeLogout(DataInputStream data) throws Exception {
        String playerUuid = data.readUTF();
        String playerName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification", Map.of(
                "player", playerName
        )));
        logger.info("[bridge] Frozen player " + playerName + " logged out");
    }

    private void handleStaffModeEnter(DataInputStream data) throws Exception {
        String staffUuid = data.readUTF();
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleStaffModeExit(DataInputStream data) throws Exception {
        String staffUuid = data.readUTF();
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleVanishEnter(DataInputStream data) throws Exception {
        UUID staffUuid = UUID.fromString(data.readUTF());
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        vanishService.vanish(staffUuid);
        platform.staffBroadcast(localeManager.getMessage("vanish.enabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleVanishExit(DataInputStream data) throws Exception {
        UUID staffUuid = UUID.fromString(data.readUTF());
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        vanishService.unvanish(staffUuid);
        platform.staffBroadcast(localeManager.getMessage("vanish.disabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleOpenStaffMenu(DataInputStream data) throws Exception {
        UUID playerUuid = UUID.fromString(data.readUTF());
        platform.dispatchPlayerCommand(playerUuid, "staffmenu");
    }

    private void handleOpenInspectMenu(DataInputStream data) throws Exception {
        UUID playerUuid = UUID.fromString(data.readUTF());
        String targetName = data.readUTF();
        platform.dispatchPlayerCommand(playerUuid, "inspect " + targetName);
    }

    private void handleTargetResponse(DataInputStream data) throws Exception {
        UUID staffUuid = UUID.fromString(data.readUTF());
        UUID targetUuid = UUID.fromString(data.readUTF());
        String targetServer = data.readUTF();
        staffModeService.setTarget(staffUuid, targetUuid);
        platform.connectToServer(staffUuid, targetServer);
        logger.info("[bridge] Target response: staff " + staffUuid + " -> target " + targetUuid + " on " + targetServer);
    }

    private void handleProxyCmd(DataInputStream data) throws Exception {
        String command = data.readUTF();
        logger.info("[bridge] Executing proxy command: " + command);
        platform.dispatchConsoleCommand(command);
    }
}
