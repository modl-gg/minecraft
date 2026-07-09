package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import gg.modl.minecraft.core.util.StaffCommandUtil;
import gg.modl.minecraft.core.util.StaffCommandUtil.StaffDisplay;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@Command("vanish") @PlayerOnly @StaffOnly @RequiredArgsConstructor
public class VanishCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final VanishService vanishService;
    private final BridgeService bridgeService;

    @Description("Toggle vanish mode")
    public void onVanish(CommandActor actor) {
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.MOD_ACTIONS)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID uuid = actor.uniqueId();
        boolean nowVanished = vanishService.toggle(uuid);

        StaffDisplay display = StaffCommandUtil.resolvePlayerDisplay(uuid, platform, cache, Constants.DEFAULT_STAFF_NAME);

        if (nowVanished) {
            actor.reply(localeManager.getMessage("vanish.enabled"));
            bridgeService.sendVanishEnter(uuid.toString(), display.getInGameName(), display.getPanelName());
        } else {
            actor.reply(localeManager.getMessage("vanish.disabled"));
            bridgeService.sendVanishExit(uuid.toString(), display.getInGameName(), display.getPanelName());
        }
    }
}
