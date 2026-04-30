package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@RequiredArgsConstructor @Command("localchat") @PlayerOnly @StaffOnly
public class LocalChatCommand {
    private static final String LOCAL_CHAT_FORMAT = "\u00a7f<%s\u00a7f> %s";

    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;

    @Description("Switch back to normal chat mode or send a local message")
    public void localChat(CommandActor actor, @revxrsal.commands.annotation.Optional @ConsumeRemaining String message) {
        if (message == null) message = "";
        UUID senderUuid = actor.uniqueId();

        if (!message.isEmpty()) {
            sendLocalMessage(senderUuid, message);
            return;
        }

        if (staffChatService.getMode(senderUuid) == ChatMode.NORMAL) {
            actor.reply(localeManager.getMessage("staff_chat.already_normal"));
            return;
        }

        staffChatService.setMode(senderUuid, ChatMode.NORMAL);
        actor.reply(localeManager.getMessage("staff_chat.disabled"));
    }

    private void sendLocalMessage(UUID senderUuid, String message) {
        AbstractPlayer player = platform.getPlayer(senderUuid);
        if (player == null) return;

        String displayName = cache.getStaffDisplayName(senderUuid);
        String name = displayName != null ? displayName : player.getName();
        platform.broadcast(String.format(LOCAL_CHAT_FORMAT, name, message));
    }
}
