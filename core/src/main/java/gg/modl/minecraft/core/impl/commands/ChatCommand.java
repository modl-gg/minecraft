package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ChatManagementConfig;
import gg.modl.minecraft.core.config.StaffChatConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Unified chat management command with subcommands for staff chat toggle,
 * chat enable/disable, chat clear, and slow mode.
 * <p>
 * Usage:
 * <ul>
 *   <li>{@code /chat staff} - Toggle staff chat mode</li>
 *   <li>{@code /chat toggle} - Enable/disable public chat</li>
 *   <li>{@code /chat clear [count]} - Clear chat with empty lines</li>
 *   <li>{@code /chat slow [seconds|off]} - Set or disable slow mode</li>
 * </ul>
 */
@RequiredArgsConstructor
@CommandAlias("%cmd_chat")
@Conditions("staff")
public class ChatCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final StaffChatConfig staffChatConfig;
    private final ChatManagementConfig chatManagementConfig;

    @Default
    @Description("Show chat command help")
    public void showHelp(CommandIssuer sender) {
        sender.sendMessage(localeManager.getMessage("chat_management.help.header"));
        sender.sendMessage(localeManager.getMessage("chat_management.help.staff"));
        sender.sendMessage(localeManager.getMessage("chat_management.help.toggle"));
        sender.sendMessage(localeManager.getMessage("chat_management.help.clear"));
        sender.sendMessage(localeManager.getMessage("chat_management.help.slow"));
    }

    // ==================== STAFF CHAT TOGGLE ====================

    @Subcommand("staff")
    @Description("Toggle staff chat mode")
    public void staff(CommandIssuer sender) {
        if (!staffChatConfig.isEnabled()) {
            sender.sendMessage(localeManager.getMessage("staff_chat.feature_disabled"));
            return;
        }

        if (!sender.isPlayer()) {
            sender.sendMessage(localeManager.getMessage("general.players_only"));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        ChatMode newMode = staffChatService.toggleStaffChat(senderUuid);

        if (newMode == ChatMode.STAFF) {
            sender.sendMessage(localeManager.getMessage("staff_chat.enabled"));
        } else {
            sender.sendMessage(localeManager.getMessage("staff_chat.disabled"));
        }
    }

    // ==================== CHAT TOGGLE ====================

    @Subcommand("toggle")
    @Description("Toggle public chat on or off")
    @Conditions("permission:value=staff.chat.toggle")
    public void toggle(CommandIssuer sender) {
        boolean newState = chatManagementService.toggleChat();

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (newState) {
            // Chat enabled
            platform.broadcast(localeManager.getMessage("chat_management.chat_toggled_on", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
        } else {
            // Chat disabled
            platform.broadcast(localeManager.getMessage("chat_management.chat_toggled_off", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
        }
    }

    // ==================== CHAT CLEAR ====================

    @Subcommand("clear")
    @Description("Clear the chat")
    @Conditions("permission:value=staff.chat.clear")
    public void clear(CommandIssuer sender, @Default("") String countArg) {
        int lines;

        if (countArg.isEmpty()) {
            lines = chatManagementConfig.getClearLines();
        } else {
            try {
                lines = Integer.parseInt(countArg);
                if (lines < 1) lines = chatManagementConfig.getClearLines();
            } catch (NumberFormatException e) {
                lines = chatManagementConfig.getClearLines();
            }
        }

        // Broadcast empty lines to clear chat
        StringBuilder clearMessage = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            clearMessage.append("\n");
        }
        platform.broadcast(clearMessage.toString());

        // Announce who cleared the chat
        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);
        platform.broadcast(localeManager.getMessage("chat_management.chat_cleared", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    // ==================== SLOW MODE ====================

    @Subcommand("slow")
    @Description("Set or disable slow mode")
    @Conditions("permission:value=staff.chat.slow")
    public void slow(CommandIssuer sender, @Default("") String secondsArg) {
        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (secondsArg.isEmpty()) {
            // No argument: use default slow mode seconds, or toggle off if already active
            if (chatManagementService.isSlowModeActive()) {
                chatManagementService.disableSlowMode();
                platform.broadcast(localeManager.getMessage("chat_management.slow_mode_disabled", Map.of(
                        "staff", panelName,
                        "in-game-name", inGameName
                )));
            } else {
                int defaultSeconds = chatManagementConfig.getDefaultSlowSeconds();
                chatManagementService.setSlowMode(defaultSeconds);
                platform.broadcast(localeManager.getMessage("chat_management.slow_mode_enabled", Map.of(
                        "staff", panelName,
                        "in-game-name", inGameName,
                        "seconds", String.valueOf(defaultSeconds)
                )));
            }
            return;
        }

        if (secondsArg.equalsIgnoreCase("off") || secondsArg.equals("0")) {
            chatManagementService.disableSlowMode();
            platform.broadcast(localeManager.getMessage("chat_management.slow_mode_disabled", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            return;
        }

        try {
            int seconds = Integer.parseInt(secondsArg);
            if (seconds < 1) {
                sender.sendMessage(localeManager.getMessage("chat_management.invalid_seconds"));
                return;
            }

            chatManagementService.setSlowMode(seconds);
            platform.broadcast(localeManager.getMessage("chat_management.slow_mode_enabled", Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName,
                    "seconds", String.valueOf(seconds)
            )));
        } catch (NumberFormatException e) {
            sender.sendMessage(localeManager.getMessage("chat_management.invalid_seconds"));
        }
    }

    // ==================== HELPERS ====================

    private String getInGameName(CommandIssuer sender) {
        if (!sender.isPlayer()) return "Console";
        var player = platform.getPlayer(sender.getUniqueId());
        return player != null ? player.username() : "Staff";
    }

    private String getPanelName(CommandIssuer sender, String fallback) {
        if (!sender.isPlayer()) return "Console";
        String display = cache.getStaffDisplayName(sender.getUniqueId());
        return display != null ? display : fallback;
    }
}
