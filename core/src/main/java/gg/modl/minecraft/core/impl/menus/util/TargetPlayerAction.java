package gg.modl.minecraft.core.impl.menus.util;

import gg.modl.minecraft.core.PluginServices;
import dev.simplix.cirrus.model.Click;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.staff.StaffCommandUtil;
import gg.modl.minecraft.core.staff.StaffCommandUtil.StaffDisplay;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class TargetPlayerAction {
    private TargetPlayerAction() {}

    public static void handle(Click click, Platform platform, UUID viewerUuid, UUID targetUuid, String targetName) {
        if (targetUuid == null) return;
        if (!PluginServices.cache().hasPermission(viewerUuid, Permissions.MOD_ACTIONS)) {
            platform.sendMessage(viewerUuid, PluginServices.locale().getMessage("general.no_permission"));
            return;
        }
        if (targetUuid.equals(viewerUuid)) {
            platform.sendMessage(viewerUuid, PluginServices.locale().getMessage("target.cannot_target_self"));
            return;
        }
        if (PluginServices.cache().getPlayerProfile(targetUuid) == null) {
            platform.sendMessage(viewerUuid, PluginServices.locale().getMessage("target.not_online"));
            return;
        }

        click.clickedMenu().close();

        StaffModeService staffModeService = PluginServices.staffMode();
        if (staffModeService == null) return;

        if (!staffModeService.isInStaffMode(viewerUuid)) {
            enterStaffMode(platform, staffModeService, viewerUuid);
        }

        staffModeService.setTarget(viewerUuid, targetUuid);
        BridgeService bridgeService = PluginServices.bridge();
        if (bridgeService != null) bridgeService.sendTargetRequest(viewerUuid.toString(), targetUuid.toString());
        platform.sendMessage(viewerUuid, PluginServices.locale().getMessage("target.targeting", mapOf("player", targetName)));
    }

    private static void enterStaffMode(Platform platform, StaffModeService staffModeService, UUID viewerUuid) {
        BridgeService bridgeService = PluginServices.bridge();
        StaffDisplay display = StaffCommandUtil.resolvePlayerDisplay(viewerUuid, platform, PluginServices.cache(), "Staff");
        StaffCommandUtil.enableStaffModeForPlayer(platform, viewerUuid, staffModeService, bridgeService,
                PluginServices.locale(), display);
    }
}
