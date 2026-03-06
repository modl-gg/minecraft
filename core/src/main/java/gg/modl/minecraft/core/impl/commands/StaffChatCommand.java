package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.StaffChatConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
@CommandAlias("%cmd_staffchat")
@Conditions("staff")
public class StaffChatCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;
    private final StaffChatConfig staffChatConfig;

    @Default
    @Description("Toggle staff chat mode or send a message to staff chat")
    public void staffChat(CommandIssuer sender, @Default("") String message) {
        if (!staffChatConfig.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("staff_chat.feature_disabled"));
            return;
        }

        if (!message.isEmpty()) {
            sendStaffChatMessage(sender, message);
            return;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        ChatMode newMode = staffChatService.toggleStaffChat(senderUuid);

        if (newMode == ChatMode.STAFF) sender.sendMessage(localeManager.getMessage("staff_chat.enabled"));
        else sender.sendMessage(localeManager.getMessage("staff_chat.disabled"));
    }

    private void sendStaffChatMessage(CommandIssuer sender, String message) {
        if (!sender.isPlayer()) {
            platform.staffBroadcast(staffChatConfig.formatMessage("Console", "Console", message));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        var player = platform.getPlayer(senderUuid);
        String inGameName = player != null ? player.getName() : "Staff";
        String display = cache.getStaffDisplayName(senderUuid);
        String panelName = display != null ? display : inGameName;

        platform.staffBroadcast(staffChatConfig.formatMessage(inGameName, panelName, message));
    }
}
