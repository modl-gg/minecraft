package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.config.ConfigManager.ChatManagementConfig;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffChatService.ChatMode;
import gg.modl.minecraft.core.util.StaffCommandUtil;
import gg.modl.minecraft.core.util.StaffCommandUtil.StaffDisplay;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;

import java.util.Collections;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

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

        StaffDisplay display = StaffCommandUtil.resolveActorDisplay(actor, platform, cache, "Console", "Staff", true);

        String messageKey = newState ? "chat_management.chat_toggled_on" : "chat_management.chat_toggled_off";
        platform.broadcast(localeManager.getMessage(messageKey, mapOf(
                    "staff", display.getPanelName(),
                    "in-game-name", display.getInGameName()
            )));
    }

    @Subcommand("clear")
    @Description("Clear the chat")
    @RequiresPermission("staff.chat.clear")
    public void clear(CommandActor actor, @Optional String countArg) {
        if (countArg == null) countArg = "";
        int lines = parseClearLineCount(countArg);

        platform.broadcast(String.join("", Collections.nCopies(Math.max(0, lines), "\n")));

        StaffDisplay display = StaffCommandUtil.resolveActorDisplay(actor, platform, cache, "Console", "Staff", true);
        platform.broadcast(localeManager.getMessage("chat_management.chat_cleared", mapOf(
                "staff", display.getPanelName(),
                "in-game-name", display.getInGameName()
        )));
    }

    @Subcommand("slow")
    @Description("Set or disable slow mode")
    @RequiresPermission("staff.chat.slow")
    public void slow(CommandActor actor, @Optional String secondsArg) {
        if (secondsArg == null) secondsArg = "";
        StaffDisplay display = StaffCommandUtil.resolveActorDisplay(actor, platform, cache, "Console", "Staff", true);

        if (secondsArg.isEmpty()) {
            actor.reply(localeManager.getMessage("chat_management.usage_slow"));
            return;
        }

        if (secondsArg.equalsIgnoreCase("off") || secondsArg.equals("0")) {
            chatManagementService.disableSlowMode();
            platform.broadcast(localeManager.getMessage("chat_management.slow_mode_disabled", mapOf(
                    "staff", display.getPanelName(),
                    "in-game-name", display.getInGameName()
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
                    "staff", display.getPanelName(),
                    "in-game-name", display.getInGameName(),
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
}
