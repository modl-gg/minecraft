package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@Command("staffmode") @PlayerOnly @StaffOnly @RequiredArgsConstructor
public class StaffModeCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffModeService staffModeService;
    private final VanishService vanishService;
    private final BridgeService bridgeService;

    @Description("Toggle staff mode")
    public void onStaffMode(CommandActor actor) {
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID uuid = actor.uniqueId();
        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);

        if (staffModeService.isInStaffMode(uuid)) {
            disableStaffMode(uuid, inGameName, panelName, actor);
        } else {
            enableStaffMode(uuid, inGameName, panelName, actor);
        }
    }

    private void disableStaffMode(UUID uuid, String inGameName, String panelName, CommandActor actor) {
        staffModeService.disable(uuid);
        vanishService.unvanish(uuid);
        actor.reply(localeManager.getMessage("staff_mode.disabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeExit(uuid.toString(), inGameName, panelName);
        bridgeService.sendVanishExit(uuid.toString(), inGameName, panelName);
    }

    private void enableStaffMode(UUID uuid, String inGameName, String panelName, CommandActor actor) {
        staffModeService.enable(uuid);
        actor.reply(localeManager.getMessage("staff_mode.enabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeEnter(uuid.toString(), inGameName, panelName);
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
