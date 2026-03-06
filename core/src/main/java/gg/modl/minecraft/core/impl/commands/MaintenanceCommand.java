package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.MaintenanceService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
@CommandAlias("%cmd_maintenance")
@Conditions("staff")
public class MaintenanceCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final MaintenanceService maintenanceService;

    @Subcommand("on")
    @Description("Enable maintenance mode")
    @Conditions("permission:value=staff.maintenance")
    public void on(CommandIssuer sender) {
        if (maintenanceService.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("maintenance.already_enabled"));
            return;
        }

        maintenanceService.enable(platform, cache, localeManager.getMessage("maintenance.kick_message"));

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);
        platform.staffBroadcast(localeManager.getMessage("maintenance.enabled", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Subcommand("off")
    @Description("Disable maintenance mode")
    @Conditions("permission:value=staff.maintenance")
    public void off(CommandIssuer sender) {
        if (!maintenanceService.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("maintenance.already_disabled"));
            return;
        }

        maintenanceService.disable();

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);
        platform.staffBroadcast(localeManager.getMessage("maintenance.disabled", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Default
    @Description("Show maintenance mode status")
    public void status(CommandIssuer sender) {
        if (maintenanceService.isEnabled()) sender.sendMessage(localeManager.getMessage("maintenance.status_enabled"));
        else sender.sendMessage(localeManager.getMessage("maintenance.status_disabled"));
    }

    private String getInGameName(CommandIssuer sender) {
        if (!sender.isPlayer()) return "Console";
        var player = platform.getPlayer(sender.getUniqueId());
        return player != null ? player.getName() : "Staff";
    }

    private String getPanelName(CommandIssuer sender, String fallback) {
        if (!sender.isPlayer()) return "Console";
        String display = cache.getStaffDisplayName(sender.getUniqueId());
        return display != null ? display : fallback;
    }
}
