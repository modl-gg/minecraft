package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@CommandAlias("%cmd_target") @Conditions("staff|player") @RequiredArgsConstructor
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
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.MOD_ACTIONS)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID staffUuid = sender.getUniqueId();

        if (target == null) {
            handleNoTarget(sender, staffUuid);
            return;
        }
        if (target.getUuid().equals(staffUuid)) {
            sender.sendMessage(localeManager.getMessage("target.cannot_target_self"));
            return;
        }

        UUID targetUuid = target.getUuid();
        String targetName = target.getName();

        connectToTargetServerIfNeeded(sender, staffUuid, targetUuid, targetName);
        ensureStaffModeEnabled(sender, staffUuid);

        staffModeService.setTarget(staffUuid, targetUuid);
        bridgeService.sendTargetRequest(staffUuid.toString(), targetUuid.toString());
        sender.sendMessage(localeManager.getMessage("target.targeting", Map.of("player", targetName)));
    }

    private void handleNoTarget(CommandIssuer sender, UUID staffUuid) {
        if (staffModeService.getState(staffUuid) == StaffModeService.StaffModeState.TARGETING) {
            staffModeService.clearTarget(staffUuid);
            sender.sendMessage(localeManager.getMessage("target.cleared"));
        } else {
            sender.sendMessage(localeManager.getMessage("target.usage"));
        }
    }

    private void connectToTargetServerIfNeeded(CommandIssuer sender, UUID staffUuid, UUID targetUuid, String targetName) {
        String staffServer = platform.getPlayerServer(staffUuid);
        String targetServer = platform.getPlayerServer(targetUuid);

        if (targetServer != null && !targetServer.equals(staffServer)) {
            platform.connectToServer(staffUuid, targetServer);
            sender.sendMessage(localeManager.getMessage("target.connecting", Map.of(
                    "player", targetName, "server", targetServer
            )));
        }
    }

    private void ensureStaffModeEnabled(CommandIssuer sender, UUID staffUuid) {
        if (staffModeService.isInStaffMode(staffUuid)) return;

        staffModeService.enable(staffUuid);

        AbstractPlayer staffPlayer = platform.getPlayer(staffUuid);
        String inGameName = staffPlayer != null ? staffPlayer.getName() : "Staff";
        String panelName = cache.getStaffDisplayName(staffUuid);
        if (panelName == null) panelName = inGameName;

        sender.sendMessage(localeManager.getMessage("staff_mode.enabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", Map.of(
                "staff", panelName, "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeEnter(staffUuid.toString(), inGameName, panelName);
    }
}
