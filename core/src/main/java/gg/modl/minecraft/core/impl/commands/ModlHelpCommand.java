package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor @CommandAlias("%cmd_modl")
public class ModlHelpCommand extends BaseCommand {
    private static final String VERSION_HEADER = "§a§lmodl.gg§a v%s§f - §eModeration and Support Management System",
            VERSION_FOOTER = "§7GNU AGPLv3 Free Software. Use /modl help for command information.";
    private static final int ENTRIES_PER_PAGE = 8;

    private final Cache cache;
    private final LocaleManager localeManager;

    @Default
    @Description("")
    public void showHelp(CommandIssuer sender) {
        sender.sendMessage(String.format(VERSION_HEADER, PluginInfo.VERSION));
        sender.sendMessage(VERSION_FOOTER);
    }

    @Subcommand("help")
    @Description("Show available modl.gg commands")
    public void help(CommandIssuer sender, @Default("1") String pageArg) {
        int page;
        try {
            page = Integer.parseInt(pageArg);
        } catch (NumberFormatException e) {
            page = 1;
        }
        if (page < 1) page = 1;

        displayHelp(sender, page);
    }

    private void displayHelp(CommandIssuer sender, int page) {
        UUID senderUuid = sender.isPlayer() ? sender.getUniqueId() : null;
        boolean isStaff = senderUuid != null && PermissionUtil.isStaff(senderUuid, cache);
        boolean isAdmin = senderUuid != null && cache.hasPermission(senderUuid, Permissions.ADMIN);

        List<HelpEntry> entries = new ArrayList<>();

        addEntry(entries, "player_commands.iammuted");
        addEntry(entries, "player_commands.standing");
        addEntry(entries, "player_commands.report");
        addEntry(entries, "player_commands.chatreport");
        addEntry(entries, "player_commands.claimticket");
        addEntry(entries, "player_commands.apply");
        addEntry(entries, "player_commands.bugreport");
        addEntry(entries, "player_commands.support");

        if (isStaff || !sender.isPlayer()) {
            addEntry(entries, "staff_commands.staffmenu");
            addEntry(entries, "staff_commands.inspect");
            addEntry(entries, "staff_commands.history");
            addEntry(entries, "staff_commands.notes");
            addEntry(entries, "staff_commands.alts");
            addEntry(entries, "staff_commands.reports");
            addEntry(entries, "staff_commands.punish");

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_MANUAL_BAN)) {
                addEntry(entries, "staff_commands.ban");
            }

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_MANUAL_MUTE)) {
                addEntry(entries, "staff_commands.mute");
            }

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_KICK)) {
                addEntry(entries, "staff_commands.kick");
            }

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_BLACKLIST)) {
                addEntry(entries, "staff_commands.blacklist");
            }

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_MODIFY)) {
                addEntry(entries, "staff_commands.pardon");
                addEntry(entries, "staff_commands.unban");
                addEntry(entries, "staff_commands.unmute");
            }

            if (isAdmin || !sender.isPlayer()) {
                addEntry(entries, "staff_commands.modl_reload");
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        if (page > totalPages) {
            sender.sendMessage(localeManager.getMessage("help.no_more_pages"));
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(localeManager.getMessage("help.header", Map.of(
                "version", PluginInfo.VERSION,
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)
        )));

        int start = (page - 1) * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            HelpEntry entry = entries.get(i);
            sender.sendMessage(localeManager.getMessage("help.entry", Map.of(
                    "command", entry.getCommand(),
                    "description", entry.getDescription()
            )));
        }

        if (page < totalPages)
            sender.sendMessage(localeManager.getMessage("help.footer", Map.of(
                    "page", String.valueOf(page),
                    "total_pages", String.valueOf(totalPages),
                    "next_page", String.valueOf(page + 1)
            )));
        sender.sendMessage("");
    }

    private void addEntry(List<HelpEntry> entries, String localeKey) {
        String usage = localeManager.getMessage("help." + localeKey + ".usage");
        if (usage == null || usage.isEmpty() || usage.startsWith("§cMissing")) return;
        String description = localeManager.getMessage("help." + localeKey + ".description");
        entries.add(new HelpEntry(usage, description));
    }

    @Data @AllArgsConstructor
    private static class HelpEntry {
        private final String command;
        private final String description;
    }
}
