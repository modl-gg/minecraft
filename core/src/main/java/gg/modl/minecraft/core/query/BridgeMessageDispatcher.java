package gg.modl.minecraft.core.query;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.bridge.protocol.BridgeAction;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;

import java.io.DataInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.Setter;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class BridgeMessageDispatcher {
    private final Platform platform;
    private final LocaleManager localeManager;
    private final FreezeService freezeService;
    private final StaffModeService staffModeService;
    private final VanishService vanishService;
    private final ModlHttpClient httpClient;
    private final PluginLogger logger;
    @Setter private BridgeReplayService bridgeReplayService;

    public BridgeMessageDispatcher(Platform platform, LocaleManager localeManager,
                                    FreezeService freezeService, StaffModeService staffModeService,
                                    VanishService vanishService, ModlHttpClient httpClient,
                                    PluginLogger logger) {
        this.platform = platform;
        this.localeManager = localeManager;
        this.freezeService = freezeService;
        this.staffModeService = staffModeService;
        this.vanishService = vanishService;
        this.httpClient = httpClient;
        this.logger = logger;
    }

    public void dispatch(String action, DataInputStream data) {
        BridgeAction bridgeAction = BridgeAction.fromWire(action);
        if (bridgeAction == null) {
            logger.debug("[bridge] Unknown action: " + action);
            return;
        }
        try {
            handle(bridgeAction, data);
        } catch (Exception e) {
            logger.warning("[bridge] Error handling " + action + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handle(BridgeAction action, DataInputStream data) throws Exception {
        switch (action) {
            case FREEZE_PLAYER:
                handleFreezePlayer(data);
                break;
            case UNFREEZE_PLAYER:
                handleUnfreezePlayer(data);
                break;
            case FREEZE_LOGOUT:
                handleFreezeLogout(data);
                break;
            case STAFF_MODE_ENTER:
                handleStaffModeEnter(data);
                break;
            case STAFF_MODE_EXIT:
                handleStaffModeExit(data);
                break;
            case VANISH_ENTER:
                handleVanishEnter(data);
                break;
            case VANISH_EXIT:
                handleVanishExit(data);
                break;
            case TARGET_RESPONSE:
                handleTargetResponse(data);
                break;
            case OPEN_STAFF_MENU:
                handleOpenStaffMenu(data);
                break;
            case OPEN_INSPECT_MENU:
                handleOpenInspectMenu(data);
                break;
            case PROXY_CMD:
                handleProxyCmd(data);
                break;
            case CREATE_REPORT:
                handleCreateReport(data);
                break;
            case CAPTURE_REPLAY_RESPONSE:
                handleCaptureReplayResponse(data);
                break;
            default:
                logger.debug("[bridge] Unhandled action: " + action.wire());
        }
    }

    private void handleFreezePlayer(DataInputStream data) throws Exception {
        UUID targetUuid = UUID.fromString(data.readUTF());
        UUID staffUuid = UUID.fromString(data.readUTF());
        freezeService.freeze(targetUuid, staffUuid);
        logger.info("[bridge] Freeze applied to " + targetUuid + " by " + staffUuid);
    }

    private void handleUnfreezePlayer(DataInputStream data) throws Exception {
        UUID targetUuid = UUID.fromString(data.readUTF());
        freezeService.unfreeze(targetUuid);
        logger.info("[bridge] Unfreeze applied to " + targetUuid);
    }

    private void handleFreezeLogout(DataInputStream data) throws Exception {
        String ignoredFrozenUuid = data.readUTF();
        String playerName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification", mapOf(
                "player", playerName
        )));
        logger.info("[bridge] Frozen player " + playerName + " logged out");
    }

    private void handleStaffModeEnter(DataInputStream data) throws Exception {
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleStaffModeExit(DataInputStream data) throws Exception {
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleVanishEnter(DataInputStream data) throws Exception {
        UUID staffUuid = UUID.fromString(data.readUTF());
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        vanishService.vanish(staffUuid);
        platform.staffBroadcast(localeManager.getMessage("vanish.enabled_broadcast", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    private void handleVanishExit(DataInputStream data) throws Exception {
        UUID staffUuid = UUID.fromString(data.readUTF());
        String inGameName = data.readUTF();
        String panelName = data.readUTF();
        vanishService.unvanish(staffUuid);
        platform.staffBroadcast(localeManager.getMessage("vanish.disabled_broadcast", mapOf(
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

    private void handleCaptureReplayResponse(DataInputStream data) throws Exception {
        UUID targetUuid = UUID.fromString(data.readUTF());
        String replayId = data.readUTF();
        ReplayCaptureStatus status = data.available() > 0
                ? ReplayCaptureStatus.fromWire(data.readUTF())
                : null;
        if (bridgeReplayService != null) {
            bridgeReplayService.handleCaptureResponse(targetUuid,
                    replayId.isEmpty() ? null : replayId,
                    status);
        }
    }

    private void handleCreateReport(DataInputStream data) throws Exception {
        String creatorUuid = data.readUTF();
        String creatorName = data.readUTF();
        String type = data.readUTF();
        String subject = data.readUTF();
        String description = data.readUTF();
        String reportedPlayerUuid = data.readUTF();
        String reportedPlayerName = data.readUTF();
        String tagsJoined = data.readUTF();
        String priority = data.readUTF();
        String createdServer = data.readUTF();

        String replayUrl = null;
        if (data.available() > 0) {
            replayUrl = data.readUTF();
            if (replayUrl.isEmpty()) replayUrl = null;
        }

        List<String> tags = tagsJoined.isEmpty() ? listOf() : Arrays.asList(tagsJoined.split(","));

        CreateTicketRequest request = new CreateTicketRequest(
                creatorUuid, type, creatorName, subject, description,
                reportedPlayerUuid, reportedPlayerName, priority, createdServer,
                null, tags, replayUrl
        );

        logger.info("[bridge] Creating report ticket for " + reportedPlayerName + ": " + subject
                + (replayUrl != null ? " (replay: " + replayUrl + ")" : ""));

        httpClient.createTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                logger.info("[bridge] Report ticket created: " + response.getTicketId());
            } else {
                logger.warning("[bridge] Failed to create report ticket: " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            logger.warning("[bridge] Error creating report ticket: " + throwable.getMessage());
            return null;
        });
    }
}
