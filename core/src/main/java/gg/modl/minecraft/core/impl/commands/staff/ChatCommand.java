package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.config.ConfigManager.ChatManagementConfig;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;

import java.util.Collections;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor @Command("chat") @StaffOnly
public class ChatCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final StaffChatConfig staffChatConfig;
    private final ChatManagementConfig chatManagementConfig;

    @Description("Show chat command help")
    public void showHelp(CommandActor actor) {
        actor.reply(localeManager.getMessage("chat_management.help.header"));
        actor.reply(localeManager.getMessage("chat_management.help.staff"));
        actor.reply(localeManager.getMessage("chat_management.help.toggle"));
        actor.reply(localeManager.getMessage("chat_management.help.clear"));
        actor.reply(localeManager.getMessage("chat_management.help.slow"));
    }

    @Subcommand("staff")
    @Description("Toggle staff chat mode")
    public void staff(CommandActor actor) {
        if (!staffChatConfig.isEnabled()) {
            actor.reply(localeManager.getMessage("staff_chat.feature_disabled"));
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

    @Subcommand("toggle")
    @Description("Toggle public chat on or off")
    @RequiresPermission("staff.chat.toggle")
    public void toggle(CommandActor actor) {
        boolean newState = chatManagementService.toggleChat();

        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);

        String messageKey = newState ? "chat_management.chat_toggled_on" : "chat_management.chat_toggled_off";
        platform.broadcast(localeManager.getMessage(messageKey, mapOf(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
    }

    @Subcommand("clear")
    @Description("Clear the chat")
    @RequiresPermission("staff.chat.clear")
    public void clear(CommandActor actor, @revxrsal.commands.annotation.Optional String countArg) {
        if (countArg == null) countArg = "";
        int lines = parseClearLineCount(countArg);

        platform.broadcast(String.join("", Collections.nCopies(Math.max(0, lines), "\n")));

        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);
        platform.broadcast(localeManager.getMessage("chat_management.chat_cleared", mapOf(
                "staff", panelName,
                "in-game-name", inGameName
        )));
    }

    @Subcommand("slow")
    @Description("Set or disable slow mode")
    @RequiresPermission("staff.chat.slow")
    public void slow(CommandActor actor, @revxrsal.commands.annotation.Optional String secondsArg) {
        if (secondsArg == null) secondsArg = "";
        String inGameName = getInGameName(actor);
        String panelName = getPanelName(actor, inGameName);

        if (secondsArg.isEmpty()) {
            actor.reply(localeManager.getMessage("chat_management.usage_slow"));
            return;
        }

        if (secondsArg.equalsIgnoreCase("off") || secondsArg.equals("0")) {
            chatManagementService.disableSlowMode();
            platform.broadcast(localeManager.getMessage("chat_management.slow_mode_disabled", mapOf(
                    "staff", panelName,
                    "in-game-name", inGameName
            )));
            return;
        }

        try {
            int seconds = Integer.parseInt(secondsArg);
            if (seconds < 1) {
                actor.reply(localeManager.getMessage("chat_management.invalid_seconds"));
                return;
            }

            chatManagementService.setSlowMode(seconds);
            platform.broadcast(localeManager.getMessage("chat_management.slow_mode_enabled", mapOf(
                    "staff", panelName,
                    "in-game-name", inGameName,
                    "seconds", String.valueOf(seconds)
            )));
        } catch (NumberFormatException e) {
            actor.reply(localeManager.getMessage("chat_management.invalid_seconds"));
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

    private String getInGameName(CommandActor actor) {
        if (actor.uniqueId() == null) return "Console";
        AbstractPlayer player = platform.getPlayer(actor.uniqueId());
        return player != null ? player.getUsername() : "Staff";
    }

    private String getPanelName(CommandActor actor, String fallback) {
        if (actor.uniqueId() == null) return "Console";
        String display = cache.getStaffDisplayName(actor.uniqueId());
        return display != null ? display : fallback;
    }
}
