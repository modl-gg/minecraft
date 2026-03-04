package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@CommandAlias("%cmd_freeze")
@Conditions("staff")
@RequiredArgsConstructor
public class FreezeCommand extends BaseCommand {

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final FreezeService freezeService;
    private final BridgeService bridgeService;

    @Default
    @CommandCompletion("@players")
    @Description("Freeze or unfreeze a player")
    public void onFreeze(CommandIssuer sender, AbstractPlayer target) {
        if (!PermissionUtil.hasPermission(sender, cache, "staff.freeze")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        java.util.UUID targetUuid = target.getUuid();
        String targetName = target.getName();

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (freezeService.isFrozen(targetUuid)) {
            // Unfreeze
            freezeService.unfreeze(targetUuid);
            platform.sendMessage(targetUuid, localeManager.getMessage("freeze.unfrozen"));
            platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_unfreeze", Map.of(
                    "player", targetName,
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            bridgeService.sendUnfreezePlayer(targetUuid.toString());
        } else {
            // Freeze
            java.util.UUID staffUuid = sender.isPlayer() ? sender.getUniqueId() : null;
            freezeService.freeze(targetUuid, staffUuid);
            platform.sendMessage(targetUuid, localeManager.getMessage("freeze.frozen_message"));
            platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_freeze", Map.of(
                    "player", targetName,
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            bridgeService.sendFreezePlayer(targetUuid.toString(), staffUuid != null ? staffUuid.toString() : "");
        }
    }

    private String getInGameName(CommandIssuer sender) {
        if (!sender.isPlayer()) return "Console";
        AbstractPlayer player = platform.getPlayer(sender.getUniqueId());
        return player != null ? player.getName() : "Staff";
    }

    private String getPanelName(CommandIssuer sender, String fallback) {
        if (!sender.isPlayer()) return "Console";
        String display = cache.getStaffDisplayName(sender.getUniqueId());
        return display != null ? display : fallback;
    }
}
