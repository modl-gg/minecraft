package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.staff.StaffCommandUtil;
import gg.modl.minecraft.core.staff.StaffCommandUtil.StaffDisplay;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@Command("freeze") @StaffOnly @RequiredArgsConstructor
public class FreezeCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final FreezeService freezeService;
    private final BridgeService bridgeService;

    @Description("Freeze or unfreeze a player")
    @RequiresPermission(Permissions.MOD_ACTIONS)
    public void onFreeze(CommandActor actor, AbstractPlayer target) {
        UUID targetUuid = target.getUuid();
        String targetName = target.getName();

        if (!platform.isOnline(targetUuid)) {
            actor.reply(localeManager.getMessage("freeze.target_offline", mapOf("player", targetName)));
            return;
        }

        StaffDisplay display = StaffCommandUtil.resolveActorDisplay(actor, platform, cache, "Console", "Staff", false);

        if (freezeService.isFrozen(targetUuid)) {
            unfreezePlayer(actor, targetUuid, targetName, display);
        } else {
            freezePlayer(actor, targetUuid, targetName, display);
        }
    }

    private void unfreezePlayer(CommandActor actor, UUID targetUuid, String targetName, StaffDisplay display) {
        freezeService.unfreeze(targetUuid);
        if (!bridgeService.sendUnfreezePlayer(targetUuid.toString())) {
            actor.reply(localeManager.getMessage("freeze.unfreeze_not_propagated", mapOf("player", targetName)));
        }
        platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_unfreeze", mapOf(
                "player", targetName,
                "staff", display.getPanelName(),
                "in-game-name", display.getInGameName()
        )));
    }

    private void freezePlayer(CommandActor actor, UUID targetUuid, String targetName, StaffDisplay display) {
        UUID staffUuid = resolveStaffUuid(actor);
        if (!bridgeService.sendFreezePlayer(targetUuid.toString(), staffUuid.toString())) {
            actor.reply(localeManager.getMessage("freeze.unavailable", mapOf("player", targetName)));
            return;
        }
        freezeService.freeze(targetUuid, staffUuid);
        platform.staffBroadcast(localeManager.getMessage("freeze.staff_notification_freeze", mapOf(
                "player", targetName,
                "staff", display.getPanelName(),
                "in-game-name", display.getInGameName()
        )));
    }

    private static UUID resolveStaffUuid(CommandActor actor) {
        UUID actorUuid = actor.uniqueId();
        return actorUuid != null ? actorUuid : CommandUtil.CONSOLE_UUID;
    }
}
