package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.util.Permissions;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@RequiredArgsConstructor @Command("interceptnetworkchat") @StaffOnly @PlayerOnly
public class InterceptNetworkChatCommand {
    private final NetworkChatInterceptService interceptService;
    private final LocaleManager localeManager;

    @Description("Toggle network chat interception")
    @RequiresPermission(Permissions.INTERCEPT)
    public void toggle(CommandActor actor) {
        UUID senderUuid = actor.uniqueId();
        boolean nowIntercepting = interceptService.toggle(senderUuid);

        if (nowIntercepting) actor.reply(localeManager.getMessage("intercept_chat.enabled"));
        else actor.reply(localeManager.getMessage("intercept_chat.disabled"));
    }
}

