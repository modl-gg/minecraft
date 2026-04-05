package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@RequiredArgsConstructor @Command("interceptnetworkchat") @StaffOnly
public class InterceptNetworkChatCommand {
    private final NetworkChatInterceptService interceptService;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Description("Toggle network chat interception")
    public void toggle(CommandActor actor) {
        if (actor.uniqueId() == null) {
            actor.reply(localeManager.getMessage("general.players_only"));
            return;
        }
        if (!PermissionUtil.hasPermission(actor, cache, Permissions.INTERCEPT)) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        UUID senderUuid = actor.uniqueId();
        boolean nowIntercepting = interceptService.toggle(senderUuid);

        if (nowIntercepting) actor.reply(localeManager.getMessage("intercept_chat.enabled"));
        else actor.reply(localeManager.getMessage("intercept_chat.disabled"));
    }
}
