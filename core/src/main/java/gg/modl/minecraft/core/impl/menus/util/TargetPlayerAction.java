package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.model.Click;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.Permissions;

import java.util.Map;
import java.util.UUID;

public final class TargetPlayerAction {
    private TargetPlayerAction() {}

    public static void handle(Click click, Platform platform, UUID viewerUuid, UUID targetUuid, String targetName) {
        if (targetUuid == null) return;
        if (platform.getCache() == null || !platform.getCache().hasPermission(viewerUuid, Permissions.MOD_ACTIONS)) {
            platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("general.no_permission"));
            return;
        }
        if (targetUuid.equals(viewerUuid)) {
            platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("target.cannot_target_self"));
            return;
        }
        if (!platform.getCache().isOnline(targetUuid)) {
            platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("target.not_online"));
            return;
        }

        click.clickedMenu().close();

        StaffModeService staffModeService = platform.getStaffModeService();
        if (staffModeService == null) return;

        if (!staffModeService.isInStaffMode(viewerUuid)) {
            enterStaffMode(platform, staffModeService, viewerUuid);
        }

        staffModeService.setTarget(viewerUuid, targetUuid);
        BridgeService bridgeService = platform.getBridgeService();
        if (bridgeService != null) bridgeService.sendTargetRequest(viewerUuid.toString(), targetUuid.toString());
        platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("target.targeting", Map.of("player", targetName)));
    }

    private static void enterStaffMode(Platform platform, StaffModeService staffModeService, UUID viewerUuid) {
        staffModeService.enable(viewerUuid);
        AbstractPlayer staffPlayer = platform.getPlayer(viewerUuid);
        String inGameName = staffPlayer != null ? staffPlayer.getName() : "Staff";
        String panelName = platform.getCache() != null ? platform.getCache().getStaffDisplayName(viewerUuid) : null;
        if (panelName == null) panelName = inGameName;
        platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("staff_mode.enabled"));
        platform.staffBroadcast(platform.getLocaleManager().getMessage("staff_mode.enabled_broadcast", Map.of(
                "staff", panelName, "in-game-name", inGameName)));
        BridgeService bridgeService = platform.getBridgeService();
        if (bridgeService != null) bridgeService.sendStaffModeEnter(viewerUuid.toString(), inGameName, panelName);
    }
}
