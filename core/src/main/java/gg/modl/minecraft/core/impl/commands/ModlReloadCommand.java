package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
@CommandAlias("modl")
public class ModlReloadCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Default
    @Description("Show available MODL commands")
    public void showHelp(CommandIssuer sender) {
        displayHelp(sender);
    }

    @Subcommand("help")
    @Description("Show available MODL commands")
    public void help(CommandIssuer sender) {
        displayHelp(sender);
    }

    private void displayHelp(CommandIssuer sender) {
        sender.sendMessage("§6=== MODL Commands ===");
        sender.sendMessage("");

        sender.sendMessage("§e§lPunishment Commands:");
        sender.sendMessage("§7/punish <player> <type> [reason]§f - Issue a punishment");
        sender.sendMessage("§7/ban <player> [duration] [reason]§f - Ban a player");
        sender.sendMessage("§7/mute <player> [duration] [reason]§f - Mute a player");
        sender.sendMessage("§7/kick <player> [reason]§f - Kick a player");
        sender.sendMessage("§7/blacklist <player> [reason]§f - Blacklist a player");
        sender.sendMessage("§7/pardon <player> [type]§f - Remove a punishment");
        sender.sendMessage("§7/unban <player>§f - Unban a player");
        sender.sendMessage("§7/unmute <player>§f - Unmute a player");
        sender.sendMessage("");

        sender.sendMessage("§e§lInformation Commands:");
        sender.sendMessage("§7/lookup <player>§f - View player information");
        sender.sendMessage("§7/modl status§f - Show plugin status");
        sender.sendMessage("");

        sender.sendMessage("§e§lPlayer Commands:");
        sender.sendMessage("§7/iammuted <player>§f - Tell someone you are muted");
        sender.sendMessage("§7/report <player> <reason>§f - Report a player");
        sender.sendMessage("§7/chatreport <messages> <reason>§f - Report chat messages");
        sender.sendMessage("§7/apply§f - Apply for staff");
        sender.sendMessage("§7/bugreport <description>§f - Report a bug");
        sender.sendMessage("§7/support <message>§f - Contact support");
        sender.sendMessage("");

        sender.sendMessage("§e§lAdmin Commands:");
        sender.sendMessage("§7/modl status§f - Check plugin status");
        sender.sendMessage("§7/modl reload§f - Reload locale files");
        sender.sendMessage("§7  Note: Staff permissions and punishment types sync automatically");
        sender.sendMessage("");

        sender.sendMessage("§6========================");
    }

    @Subcommand("reload")
    @Description("Reload MODL locale files")
    @Syntax("reload [component]")
    @Conditions("admin")
    public void reload(CommandIssuer sender, @Default("locale") String component) {
        switch (component.toLowerCase()) {
            case "all":
            case "locale":
            case "locales":
            case "messages":
                reloadLocale(sender);
                break;
            default:
                sender.sendMessage(localeManager.getMessage("reload.invalid_component",
                    Map.of("component", component, "valid", "locale")));
        }
    }

    @Subcommand("status")
    @Description("Show MODL plugin status and loaded data")
    @Conditions("admin")
    public void status(CommandIssuer sender) {
        sender.sendMessage("§6=== MODL Plugin Status ===");

        sender.sendMessage("§eStaff Permissions: §f" + cache.getStaffCount() + " staff members cached");
        sender.sendMessage("§eCached Players: §f" + cache.getCachedPlayerCount() + " players with punishment data");
        sender.sendMessage("§eLocale: §f" + localeManager.getCurrentLocale());
        sender.sendMessage("§7Note: Staff permissions and punishment types sync automatically");

        sender.sendMessage("§6========================");
    }

    private void reloadLocale(CommandIssuer sender) {
        sender.sendMessage("§6[MODL] §eReloading locale files...");
        try {
            localeManager.reloadLocale();
            sender.sendMessage("§6[MODL] §aLocale files reloaded successfully!");
        } catch (Exception e) {
            sender.sendMessage("§c[MODL] Failed to reload locale: " + e.getMessage());
        }
    }
}
