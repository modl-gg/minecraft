package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@CommandAlias("%cmd_vanish") @Conditions("staff|player") @RequiredArgsConstructor
public class VanishCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final VanishService vanishService;
    private final BridgeService bridgeService;

    @Default
    @Description("Toggle vanish mode")
    public void onVanish(CommandIssuer sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.MOD_ACTIONS)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID uuid = sender.getUniqueId();
        boolean nowVanished = vanishService.toggle(uuid);

        String inGameName = resolveInGameName(uuid);
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;

        if (nowVanished) {
            sender.sendMessage(localeManager.getMessage("vanish.enabled"));
            bridgeService.sendVanishEnter(uuid.toString(), inGameName, panelName);
        } else {
            sender.sendMessage(localeManager.getMessage("vanish.disabled"));
            bridgeService.sendVanishExit(uuid.toString(), inGameName, panelName);
        }
    }

    private String resolveInGameName(UUID uuid) {
        var player = platform.getPlayer(uuid);
        return player != null ? player.getName() : Constants.DEFAULT_STAFF_NAME;
    }
}
