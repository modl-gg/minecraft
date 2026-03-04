package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@CommandAlias("%cmd_target")
@Conditions("staff|player")
@RequiredArgsConstructor
public class TargetCommand extends BaseCommand {

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    @Default
    @CommandCompletion("@players")
    @Description("Target a player for moderation")
    public void onTarget(CommandIssuer sender, @Optional AbstractPlayer target) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }
        if (!PermissionUtil.hasPermission(sender, cache, "staff.target")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        java.util.UUID staffUuid = sender.getUniqueId();

        // No target specified - clear target if currently targeting
        if (target == null) {
            if (staffModeService.getState(staffUuid) == StaffModeService.StaffModeState.TARGETING) {
                staffModeService.clearTarget(staffUuid);
                sender.sendMessage(localeManager.getMessage("target.cleared"));
            } else {
                sender.sendMessage(localeManager.getMessage("target.usage"));
            }
            return;
        }

        // Set target
        java.util.UUID targetUuid = target.getUuid();
        String targetName = target.getName();

        // Check if target is on a different server (proxy only)
        String staffServer = platform.getPlayerServer(staffUuid);
        String targetServer = platform.getPlayerServer(targetUuid);

        if (targetServer != null && !targetServer.equals(staffServer)) {
            // Connect staff to target's server
            platform.connectToServer(staffUuid, targetServer);
            sender.sendMessage(localeManager.getMessage("target.connecting", Map.of(
                    "player", targetName,
                    "server", targetServer
            )));
        }

        // Enable staff mode if not already in it
        if (!staffModeService.isInStaffMode(staffUuid)) {
            staffModeService.enable(staffUuid);
        }

        staffModeService.setTarget(staffUuid, targetUuid);
        bridgeService.sendTargetRequest(staffUuid.toString(), targetUuid.toString());
        sender.sendMessage(localeManager.getMessage("target.targeting", Map.of("player", targetName)));
    }
}
