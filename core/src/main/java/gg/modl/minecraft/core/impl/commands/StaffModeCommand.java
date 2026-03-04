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
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@CommandAlias("%cmd_staffmode")
@Conditions("staff|player")
@RequiredArgsConstructor
public class StaffModeCommand extends BaseCommand {

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffModeService staffModeService;
    private final VanishService vanishService;
    private final BridgeService bridgeService;

    @Default
    @Description("Toggle staff mode")
    public void onStaffMode(CommandIssuer sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }
        if (!PermissionUtil.hasPermission(sender, cache, "staff.staffmode")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        java.util.UUID uuid = sender.getUniqueId();

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (staffModeService.isInStaffMode(uuid)) {
            // Disable staff mode
            staffModeService.disable(uuid);
            vanishService.unvanish(uuid);
            sender.sendMessage(localeManager.getMessage("staff_mode.disabled"));
            platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            bridgeService.sendStaffModeExit(uuid.toString(), inGameName, panelName);
            bridgeService.sendVanishExit(uuid.toString(), inGameName, panelName);
        } else {
            // Enable staff mode
            staffModeService.enable(uuid);
            sender.sendMessage(localeManager.getMessage("staff_mode.enabled"));
            platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            bridgeService.sendStaffModeEnter(uuid.toString(), inGameName, panelName);
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
