package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Command("target") @PlayerOnly @StaffOnly @RequiredArgsConstructor
public class TargetCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    @Description("Target a player for moderation")
    public void onTarget(CommandActor actor, @revxrsal.commands.annotation.Optional AbstractPlayer target) {
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID staffUuid = actor.uniqueId();

        if (target == null) {
            handleNoTarget(actor, staffUuid);
            return;
        }
        if (target.getUuid().equals(staffUuid)) {
            actor.reply(localeManager.getMessage("target.cannot_target_self"));
            return;
        }

        UUID targetUuid = target.getUuid();
        String targetName = target.getName();

        connectToTargetServerIfNeeded(actor, staffUuid, targetUuid, targetName);
        ensureStaffModeEnabled(actor, staffUuid);

        staffModeService.setTarget(staffUuid, targetUuid);
        bridgeService.sendTargetRequest(staffUuid.toString(), targetUuid.toString());
        actor.reply(localeManager.getMessage("target.targeting", mapOf("player", targetName)));
    }

    private void handleNoTarget(CommandActor actor, UUID staffUuid) {
        if (staffModeService.getState(staffUuid) == StaffModeService.StaffModeState.TARGETING) {
            staffModeService.clearTarget(staffUuid);
            actor.reply(localeManager.getMessage("target.cleared"));
        } else {
            actor.reply(localeManager.getMessage("target.usage"));
        }
    }

    private void connectToTargetServerIfNeeded(CommandActor actor, UUID staffUuid, UUID targetUuid, String targetName) {
        String staffServer = platform.getPlayerServer(staffUuid);
        String targetServer = platform.getPlayerServer(targetUuid);

        if (targetServer != null && !targetServer.equals(staffServer)) {
            platform.connectToServer(staffUuid, targetServer);
            actor.reply(localeManager.getMessage("target.connecting", mapOf(
                    "player", targetName, "server", targetServer
            )));
        }
    }

    private void ensureStaffModeEnabled(CommandActor actor, UUID staffUuid) {
        if (staffModeService.isInStaffMode(staffUuid)) return;

        staffModeService.enable(staffUuid);

        AbstractPlayer staffPlayer = platform.getPlayer(staffUuid);
        String inGameName = staffPlayer != null ? staffPlayer.getName() : "Staff";
        String panelName = cache.getStaffDisplayName(staffUuid);
        if (panelName == null) panelName = inGameName;

        actor.reply(localeManager.getMessage("staff_mode.enabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", mapOf(
                "staff", panelName, "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeEnter(staffUuid.toString(), inGameName, panelName);
    }
}
