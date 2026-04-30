package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Command("freeze") @StaffOnly @RequiredArgsConstructor
public class FreezeCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final FreezeService freezeService;
    private final BridgeService bridgeService;

    @Description("Freeze or unfreeze a player")
    public void onFreeze(CommandActor actor, AbstractPlayer target) {
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID targetUuid = target.getUuid();
        String targetName = target.getName();
        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);

        if (freezeService.isFrozen(targetUuid)) {
            unfreezePlayer(targetUuid, targetName, inGameName, panelName);
        } else {
            freezePlayer(actor, targetUuid, targetName, inGameName, panelName);
        }
    }

    private void unfreezePlayer(UUID targetUuid, String targetName, String inGameName, String panelName) {
        freezeService.unfreeze(targetUuid);
        platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_unfreeze", mapOf(
                "player", targetName,
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendUnfreezePlayer(targetUuid.toString());
    }

    private void freezePlayer(CommandActor actor, UUID targetUuid, String targetName, String inGameName, String panelName) {
        UUID staffUuid = actor.uniqueId() != null ? actor.uniqueId() : null;
        freezeService.freeze(targetUuid, staffUuid);
        platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_freeze", mapOf(
                "player", targetName,
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendFreezePlayer(targetUuid.toString(), staffUuid != null ? staffUuid.toString() : "");
    }

    private String getInGameName(CommandActor actor) {
        if (actor.uniqueId() == null) return "Console";
        AbstractPlayer player = platform.getPlayer(actor.uniqueId());
        return player != null ? player.getName() : "Staff";
    }

    private String getPanelName(CommandActor actor, String fallback) {
        if (actor.uniqueId() == null) return "Console";
        String display = cache.getStaffDisplayName(actor.uniqueId());
        return display != null ? display : fallback;
    }
}
