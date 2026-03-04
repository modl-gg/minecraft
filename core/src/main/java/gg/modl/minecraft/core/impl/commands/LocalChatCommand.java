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
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Command to switch out of staff chat mode back to normal (local) chat.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code /localchat} - Switch back to normal chat mode</li>
 *   <li>{@code /localchat <message>} - Send a single message in local chat without changing mode</li>
 * </ul>
 */
@RequiredArgsConstructor
@CommandAlias("%cmd_localchat")
@Conditions("player|staff")
public class LocalChatCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;

    @Default
    @Description("Switch back to normal chat mode or send a local message")
    public void localChat(CommandIssuer sender, @Default("") String message) {
        UUID senderUuid = sender.getUniqueId();

        if (message.isEmpty()) {
            // Toggle back to normal mode
            ChatMode currentMode = staffChatService.getMode(senderUuid);

            if (currentMode == ChatMode.NORMAL) {
                sender.sendMessage(localeManager.getMessage("staff_chat.already_normal"));
                return;
            }

            staffChatService.setMode(senderUuid, ChatMode.NORMAL);
            sender.sendMessage(localeManager.getMessage("staff_chat.disabled"));
        } else {
            // Send a single message in local chat (bypass staff chat mode)
            var player = platform.getPlayer(senderUuid);
            if (player != null) {
                String displayName = cache.getStaffDisplayName(senderUuid);
                String name = displayName != null ? displayName : player.getName();
                platform.broadcast("§f<" + name + "§f> " + message);
            }
        }
    }
}
