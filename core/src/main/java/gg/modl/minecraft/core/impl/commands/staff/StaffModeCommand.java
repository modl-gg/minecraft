package gg.modl.minecraft.core.impl.commands.staff;

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
import gg.modl.minecraft.core.util.StaffCommandUtil;
import gg.modl.minecraft.core.util.StaffCommandUtil.StaffDisplay;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

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
        StaffDisplay display = StaffCommandUtil.resolveActorDisplay(actor, platform, cache, "Console", "Staff", false);

        if (staffModeService.isInStaffMode(uuid)) {
            disableStaffMode(uuid, display, actor);
        } else {
            StaffCommandUtil.enableStaffModeForActor(actor, uuid, staffModeService, platform, bridgeService,
                    localeManager, display);
        }
    }

    private void disableStaffMode(UUID uuid, StaffDisplay display, CommandActor actor) {
        staffModeService.disable(uuid);
        vanishService.unvanish(uuid);
        actor.reply(localeManager.getMessage("staff_mode.disabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", mapOf(
                "staff", display.getPanelName(),
                "in-game-name", display.getInGameName()
        )));
        bridgeService.sendStaffModeExit(uuid.toString(), display.getInGameName(), display.getPanelName());
        bridgeService.sendVanishExit(uuid.toString(), display.getInGameName(), display.getPanelName());
    }
}
