package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.model.Click;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.StaffCommandUtil;
import gg.modl.minecraft.core.util.StaffCommandUtil.StaffDisplay;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

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
        if (platform.getCache().getPlayerProfile(targetUuid) == null) {
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
        platform.sendMessage(viewerUuid, platform.getLocaleManager().getMessage("target.targeting", mapOf("player", targetName)));
    }

    private static void enterStaffMode(Platform platform, StaffModeService staffModeService, UUID viewerUuid) {
        BridgeService bridgeService = platform.getBridgeService();
        StaffDisplay display = StaffCommandUtil.resolvePlayerDisplay(viewerUuid, platform, platform.getCache(), "Staff");
        StaffCommandUtil.enableStaffModeForPlayer(platform, viewerUuid, staffModeService, bridgeService,
                platform.getLocaleManager(), display);
    }
}
