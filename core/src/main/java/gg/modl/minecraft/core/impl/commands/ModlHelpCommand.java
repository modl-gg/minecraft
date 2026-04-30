package gg.modl.minecraft.core.impl.commands;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.util.Pagination;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.Permissions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor @Command("modl")
public class ModlHelpCommand {
    private static final String VERSION_HEADER = "§a§lmodl.gg§a v%s§f - §eModeration and Support Management System",
            VERSION_FOOTER = "§7GNU AGPLv3 Free Software. Use /modl help for command information.";
    private static final int ENTRIES_PER_PAGE = 8;

    private final Cache cache;
    private final LocaleManager localeManager;

    @Description("")
    public void showHelp(CommandActor actor) {
        actor.reply(String.format(VERSION_HEADER, PluginInfo.VERSION));
        actor.reply(VERSION_FOOTER);
    }

    @Subcommand("help")
    @Description("Show available modl.gg commands")
    public void help(CommandActor actor, @revxrsal.commands.annotation.Default("1") String pageArg) {
        displayHelp(actor, Pagination.parsePage(pageArg));
    }

    private void displayHelp(CommandActor actor, int page) {
        UUID senderUuid = actor.uniqueId();
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

        if (isStaff || senderUuid == null) {
            addEntry(entries, "staff_commands.staffmenu");
            addEntry(entries, "staff_commands.inspect");
            addEntry(entries, "staff_commands.history");
            addEntry(entries, "staff_commands.notes");
            addEntry(entries, "staff_commands.alts");
            addEntry(entries, "staff_commands.reports");
            addEntry(entries, "staff_commands.punish");

            if (senderUuid == null || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_MANUAL_BAN)) {
                addEntry(entries, "staff_commands.ban");
            }

            if (senderUuid == null || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_MANUAL_MUTE)) {
                addEntry(entries, "staff_commands.mute");
            }

            if (senderUuid == null || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_KICK)) {
                addEntry(entries, "staff_commands.kick");
            }

            if (senderUuid == null || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_APPLY_BLACKLIST)) {
                addEntry(entries, "staff_commands.blacklist");
            }

            if (senderUuid == null || cache.hasPermission(senderUuid, Permissions.PUNISHMENT_MODIFY)) {
                addEntry(entries, "staff_commands.pardon");
                addEntry(entries, "staff_commands.unban");
                addEntry(entries, "staff_commands.unmute");
            }

            addEntry(entries, "staff_commands.replay");

            if (isAdmin || senderUuid == null) {
                addEntry(entries, "staff_commands.modl_reload");
            }
        }

        Pagination.Page pg = Pagination.paginate(entries, ENTRIES_PER_PAGE, page);
        if (pg.isOutOfRange()) {
            actor.reply(localeManager.getMessage("help.no_more_pages"));
            return;
        }

        actor.reply("");
        actor.reply(localeManager.getMessage("help.header", mapOf(
                "version", PluginInfo.VERSION,
                "page", String.valueOf(pg.getPage()),
                "total_pages", String.valueOf(pg.getTotalPages())
        )));

        for (int i = pg.getStart(); i < pg.getEnd(); i++) {
            HelpEntry entry = entries.get(i);
            actor.reply(localeManager.getMessage("help.entry", mapOf(
                    "command", entry.getCommand(),
                    "description", entry.getDescription()
            )));
        }

        if (pg.hasNextPage())
            actor.reply(localeManager.getMessage("help.footer", mapOf(
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages()),
                    "next_page", String.valueOf(pg.getPage() + 1)
            )));
        actor.reply("");
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
