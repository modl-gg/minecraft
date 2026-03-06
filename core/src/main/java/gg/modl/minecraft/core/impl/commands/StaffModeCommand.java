package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

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
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.MOD_ACTIONS)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID uuid = sender.getUniqueId();
        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (staffModeService.isInStaffMode(uuid)) {
            disableStaffMode(uuid, inGameName, panelName, sender);
        } else {
            enableStaffMode(uuid, inGameName, panelName, sender);
        }
    }

    private void disableStaffMode(UUID uuid, String inGameName, String panelName, CommandIssuer sender) {
        staffModeService.disable(uuid);
        vanishService.unvanish(uuid);
        sender.sendMessage(localeManager.getMessage("staff_mode.disabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.disabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeExit(uuid.toString(), inGameName, panelName);
        bridgeService.sendVanishExit(uuid.toString(), inGameName, panelName);
    }

    private void enableStaffMode(UUID uuid, String inGameName, String panelName, CommandIssuer sender) {
        staffModeService.enable(uuid);
        sender.sendMessage(localeManager.getMessage("staff_mode.enabled"));
        platform.staffBroadcast(localeManager.getMessage("staff_mode.enabled_broadcast", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
        bridgeService.sendStaffModeEnter(uuid.toString(), inGameName, panelName);
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
