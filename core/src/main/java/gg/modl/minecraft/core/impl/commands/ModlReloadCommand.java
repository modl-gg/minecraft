package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.plugin.PluginInfo;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
@CommandAlias("modl")
public class ModlReloadCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Default
    @Description("")
    public void showHelp(CommandIssuer sender) {
        sender.sendMessage("§a§lmodl.gg§a v" + PluginInfo.VERSION + "§f - §eModeration and Support Management System");
        sender.sendMessage("§7GNU AGPLv3 Free Software. Use /modl help for command information.");
    }

    @Subcommand("help")
    @Description("Show available MODL commands")
    public void help(CommandIssuer sender) {
        displayHelp(sender);
    }

    private void displayHelp(CommandIssuer sender) {
        sender.sendMessage("");

        java.util.UUID senderUuid = sender.isPlayer() ? sender.getUniqueId() : null;
        boolean isStaff = senderUuid != null && cache.isStaffMember(senderUuid);
        boolean isAdmin = senderUuid != null && cache.hasPermission(senderUuid, "modl.admin");

        // Punishment Commands - show if staff
        if (isStaff) {
            sender.sendMessage("§7/staffmenu§f - Open staff menu");
            sender.sendMessage("§7/inspect <player> [-p]§f - Open player inspection menu (-p for print)");
            sender.sendMessage("§7/history <player> [-p]§f - Open player history menu (-p for print)");
            sender.sendMessage("§7/notes <player> [-p]§f - Open player notes menu (-p for print)");
            sender.sendMessage("§7/alts <player> [-p]§f - Open player alts menu (-p for print)");
            sender.sendMessage("§7/reports [player] [-p]§f - Open reports menu (-p for print)");
            sender.sendMessage("§7/punish <player> [type] [reason] [-s] [-ab] [-sw] [-lenient|regular|severe]");

            if (cache.hasPermission(senderUuid, "punishment.apply.manual-ban")) {
                sender.sendMessage("§7/ban <player> [duration] [reason]§f - Ban a player");
            }
            if (cache.hasPermission(senderUuid, "punishment.apply.manual-mute")) {
                sender.sendMessage("§7/mute <player> [duration] [reason]§f - Mute a player");
            }
            if (cache.hasPermission(senderUuid, "punishment.apply.kick")) {
                sender.sendMessage("§7/kick <player> [reason]§f - Kick a player");
            }
            if (cache.hasPermission(senderUuid, "punishment.apply.blacklist")) {
                sender.sendMessage("§7/blacklist <player> [reason]§f - Blacklist a player");
            }
            if (cache.hasPermission(senderUuid, "punishment.modify.pardon")) {
                sender.sendMessage("§7/pardon <player>§f - Pardon ALL active/unstarted punishments");
                sender.sendMessage("§7/unban <player>§f - Pardon active or oldest unstarted ban");
                sender.sendMessage("§7/unmute <player>§f - Pardon active or oldest unstarted mute");
            }
        }

        // Player Commands - always show
        sender.sendMessage("§7/iammuted <player>§f - Tell someone you are muted");
        sender.sendMessage("§7/report <player> <reason>§f - Report a player");
        sender.sendMessage("§7/chatreport <player>§f - Report a player's chat messages");
        sender.sendMessage("§7/apply§f - Apply for a staff position");
        sender.sendMessage("§7/bugreport <title>§f - Report a bug");
        sender.sendMessage("§7/support <title>§f - Open support ticket");

        // Admin Commands - show if admin
        if (isAdmin || !sender.isPlayer()) {
            sender.sendMessage("§7/modl reload§f - Reload configuration");
            sender.sendMessage("");
        }
    }

    @Subcommand("reload")
    @Description("Reload MODL configuration and locale files")
    @Conditions("admin")
    public void reload(CommandIssuer sender) {
        sender.sendMessage(localeManager.getMessage("general.reloading"));
        try {
            localeManager.reloadLocale();
            sender.sendMessage(localeManager.getMessage("general.reload_success"));
        } catch (Exception e) {
            sender.sendMessage(localeManager.getMessage("general.reload_error", Map.of("error", e.getMessage())));
        }
    }
}
