package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ConfigManager.ChatManagementConfig;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor @CommandAlias("%cmd_chat") @Conditions("staff")
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

        if (newMode == ChatMode.STAFF) sender.sendMessage(localeManager.getMessage("staff_chat.enabled"));
        else sender.sendMessage(localeManager.getMessage("staff_chat.disabled"));
    }

    @Subcommand("toggle")
    @Description("Toggle public chat on or off")
    @Conditions("permission:value=staff.chat.toggle")
    public void toggle(CommandIssuer sender) {
        boolean newState = chatManagementService.toggleChat();

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        String messageKey = newState ? "chat_management.chat_toggled_on" : "chat_management.chat_toggled_off";
        platform.broadcast(localeManager.getMessage(messageKey, Map.of(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
    }

    @Subcommand("clear")
    @Description("Clear the chat")
    @Conditions("permission:value=staff.chat.clear")
    public void clear(CommandIssuer sender, @Default() String countArg) {
        int lines = parseClearLineCount(countArg);

        platform.broadcast("\n".repeat(Math.max(0, lines)));

        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);
        platform.broadcast(localeManager.getMessage("chat_management.chat_cleared", Map.of(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Subcommand("slow")
    @Description("Set or disable slow mode")
    @Conditions("permission:value=staff.chat.slow")
    public void slow(CommandIssuer sender, @Default() String secondsArg) {
        String inGameName = getInGameName(sender);
        String panelName = getPanelName(sender, inGameName);

        if (secondsArg.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("chat_management.usage_slow"));
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

    private int parseClearLineCount(String countArg) {
        if (countArg.isEmpty()) return chatManagementConfig.getClearLines();
        try {
            int parsed = Integer.parseInt(countArg);
            return parsed >= 1 ? parsed : chatManagementConfig.getClearLines();
        } catch (NumberFormatException e) {
            return chatManagementConfig.getClearLines();
        }
    }

    private String getInGameName(CommandIssuer sender) {
        if (!sender.isPlayer()) return "Console";
        AbstractPlayer player = platform.getPlayer(sender.getUniqueId());
        return player != null ? player.getUsername() : "Staff";
    }

    private String getPanelName(CommandIssuer sender, String fallback) {
        if (!sender.isPlayer()) return "Console";
        String display = cache.getStaffDisplayName(sender.getUniqueId());
        return display != null ? display : fallback;
    }
}
