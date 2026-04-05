package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;

import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor @Command("maintenance") @StaffOnly
public class MaintenanceCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final MaintenanceService maintenanceService;

    @Subcommand("on")
    @Description("Enable maintenance mode")
    @RequiresPermission("staff.maintenance")
    public void on(CommandActor actor) {
        if (maintenanceService.isEnabled()) {
            actor.reply(localeManager.getMessage("maintenance.already_enabled"));
            return;
        }

        maintenanceService.enable(platform, cache, localeManager.getMessage("maintenance.kick_message"));

        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);
        platform.staffBroadcast(localeManager.getMessage("maintenance.enabled", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Subcommand("off")
    @Description("Disable maintenance mode")
    @RequiresPermission("staff.maintenance")
    public void off(CommandActor actor) {
        if (!maintenanceService.isEnabled()) {
            actor.reply(localeManager.getMessage("maintenance.already_disabled"));
            return;
        }

        maintenanceService.disable();

        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);
        platform.staffBroadcast(localeManager.getMessage("maintenance.disabled", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Description("Show maintenance mode status")
    public void status(CommandActor actor) {
        if (maintenanceService.isEnabled()) actor.reply(localeManager.getMessage("maintenance.status_enabled"));
        else actor.reply(localeManager.getMessage("maintenance.status_disabled"));
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
