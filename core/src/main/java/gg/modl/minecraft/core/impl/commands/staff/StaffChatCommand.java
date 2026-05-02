package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.ConsumeRemaining;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import gg.modl.minecraft.core.util.StaffCommandUtil;
import gg.modl.minecraft.core.util.StaffCommandUtil.StaffDisplay;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;

import java.util.UUID;

@RequiredArgsConstructor @Command("staffchat") @StaffOnly
public class StaffChatCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;
    private final StaffChatConfig staffChatConfig;

    @Description("Toggle staff chat mode or send a message to staff chat")
    public void staffChat(CommandActor actor, @Optional @ConsumeRemaining String message) {
        if (message == null) message = "";

        if (!staffChatConfig.isEnabled()) {
            actor.reply(localeManager.getMessage("staff_chat.feature_disabled"));
            return;
        }

        if (!message.isEmpty()) {
            sendStaffChatMessage(actor, message);
            return;
        }

        if (actor.uniqueId() == null) {
            actor.reply(localeManager.getMessage("general.players_only"));
            return;
        }

        UUID senderUuid = actor.uniqueId();
        ChatMode newMode = staffChatService.toggleStaffChat(senderUuid);

        if (newMode == ChatMode.STAFF) actor.reply(localeManager.getMessage("staff_chat.enabled"));
        else actor.reply(localeManager.getMessage("staff_chat.disabled"));
    }

    private void sendStaffChatMessage(CommandActor actor, String message) {
        if (actor.uniqueId() == null) {
            platform.staffBroadcast(staffChatConfig.formatMessage("Console", "Console", message));
            return;
        }

        UUID senderUuid = actor.uniqueId();
        StaffDisplay display = StaffCommandUtil.resolvePlayerDisplay(senderUuid, platform, cache, "Staff");

        platform.staffBroadcast(staffChatConfig.formatMessage(display.getInGameName(), display.getPanelName(), message));
    }
}
