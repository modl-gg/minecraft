package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor @CommandAlias("%cmd_interceptnetworkchat") @Conditions("staff")
public class InterceptNetworkChatCommand extends BaseCommand {
    private final NetworkChatInterceptService interceptService;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Default
    @Description("Toggle network chat interception")
    public void toggle(CommandIssuer sender) {
        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }
        if (!PermissionUtil.hasPermission(sender, cache, Permissions.INTERCEPT)) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        boolean nowIntercepting = interceptService.toggle(senderUuid);

        if (nowIntercepting) sender.sendMessage(localeManager.getMessage("intercept_chat.enabled"));
        else sender.sendMessage(localeManager.getMessage("intercept_chat.disabled"));
    }
}
