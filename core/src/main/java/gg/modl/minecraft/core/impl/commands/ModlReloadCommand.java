package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.plugin.PluginInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandAlias("%cmd_modl")
public class ModlReloadCommand extends BaseCommand {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final Runnable reloadHook;

    public ModlReloadCommand(Platform platform, Cache cache, LocaleManager localeManager) {
        this(platform, cache, localeManager, () -> {});
    }

    public ModlReloadCommand(Platform platform, Cache cache, LocaleManager localeManager, Runnable reloadHook) {
        this.platform = platform;
        this.cache = cache;
        this.localeManager = localeManager;
        this.reloadHook = reloadHook != null ? reloadHook : () -> {};
    }

    @Default
    @Description("")
    public void showHelp(CommandIssuer sender) {
        sender.sendMessage("§a§lmodl.gg§a v" + PluginInfo.VERSION + "§f - §eModeration and Support Management System");
        sender.sendMessage("§7GNU AGPLv3 Free Software. Use /modl help for command information.");
    }

    private static final int ENTRIES_PER_PAGE = 8;

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
        java.util.UUID senderUuid = sender.isPlayer() ? sender.getUniqueId() : null;
        boolean isStaff = senderUuid != null && cache.isStaffMember(senderUuid);
        boolean isAdmin = senderUuid != null && cache.hasPermission(senderUuid, "modl.admin");

        // Build command list: player commands first, then staff commands for staff members
        List<HelpEntry> entries = new ArrayList<>();

        // Player commands - everyone sees these
        addEntry(entries, "player_commands.iammuted");
        addEntry(entries, "player_commands.standing");
        addEntry(entries, "player_commands.report");
        addEntry(entries, "player_commands.chatreport");
        addEntry(entries, "player_commands.claimticket");
        addEntry(entries, "player_commands.apply");
        addEntry(entries, "player_commands.bugreport");
        addEntry(entries, "player_commands.support");

        // Staff commands - only staff see these, appended after player commands
        if (isStaff || !sender.isPlayer()) {
            addEntry(entries, "staff_commands.staffmenu");
            addEntry(entries, "staff_commands.inspect");
            addEntry(entries, "staff_commands.history");
            addEntry(entries, "staff_commands.notes");
            addEntry(entries, "staff_commands.alts");
            addEntry(entries, "staff_commands.reports");
            addEntry(entries, "staff_commands.punish");

            if (!sender.isPlayer() || cache.hasPermission(senderUuid, "punishment.apply.manual-ban")) {
                addEntry(entries, "staff_commands.ban");
            }
            if (!sender.isPlayer() || cache.hasPermission(senderUuid, "punishment.apply.manual-mute")) {
                addEntry(entries, "staff_commands.mute");
            }
            if (!sender.isPlayer() || cache.hasPermission(senderUuid, "punishment.apply.kick")) {
                addEntry(entries, "staff_commands.kick");
            }
            if (!sender.isPlayer() || cache.hasPermission(senderUuid, "punishment.apply.blacklist")) {
                addEntry(entries, "staff_commands.blacklist");
            }
            if (!sender.isPlayer() || cache.hasPermission(senderUuid, "punishment.modify")) {
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

        // Header
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
                    "command", entry.command,
                    "description", entry.description
            )));
        }

        // Footer
        if (page < totalPages) {
            sender.sendMessage(localeManager.getMessage("help.footer", Map.of(
                    "page", String.valueOf(page),
                    "total_pages", String.valueOf(totalPages),
                    "next_page", String.valueOf(page + 1)
            )));
        }
        sender.sendMessage("");
    }

    private void addEntry(List<HelpEntry> entries, String localeKey) {
        String usage = localeManager.getMessage("help." + localeKey + ".usage");
        // Skip entries with blank usage (command disabled via empty alias)
        if (usage == null || usage.isEmpty() || usage.startsWith("§cMissing")) return;
        String description = localeManager.getMessage("help." + localeKey + ".description");
        entries.add(new HelpEntry(usage, description));
    }

    private static class HelpEntry {
        final String command;
        final String description;

        HelpEntry(String command, String description) {
            this.command = command;
            this.description = description;
        }
    }

    @Subcommand("reload")
    @Description("Reload modl.gg configuration and locale files")
    @Conditions("admin")
    public void reload(CommandIssuer sender) {
        sender.sendMessage(localeManager.getMessage("general.reloading"));
        try {
            localeManager.reloadLocale();
            reloadHook.run();
            sender.sendMessage(localeManager.getMessage("general.reload_success"));
        } catch (Exception e) {
            sender.sendMessage(localeManager.getMessage("general.reload_error", Map.of("error", e.getMessage())));
        }
    }
}
