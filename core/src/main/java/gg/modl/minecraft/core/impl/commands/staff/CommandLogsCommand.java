package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.http.CommandLogEntry;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.command.RequiresPermission;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.Permissions;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

@Command("commandlogs") @StaffOnly
public class CommandLogsCommand extends AbstractLogCommand<CommandLogEntry> {
    private final ChatCommandLogService logService;

    public CommandLogsCommand(HttpClientHolder httpClientHolder, ChatCommandLogService logService, LocaleManager localeManager, DateFormatter dateFormatter) {
        super(httpClientHolder, localeManager, dateFormatter);
        this.logService = logService;
    }

    @Description("View recent commands for a player")
    @RequiresPermission(Permissions.COMMAND_LOGS)
    public void commandLogs(CommandActor actor, @Named("player") String playerQuery, @Optional String pageArg) {
        if (pageArg == null) pageArg = "1";
        execute(actor, playerQuery, pageArg);
    }

    @Override protected String localePrefix() { return "command_logs"; }

    @Override
    protected CompletableFuture<List<CommandLogEntry>> fetchEntries(String uuid) {
        return logService.getCommandLogs(httpClientHolder, uuid, 200);
    }

    @Override protected long getTimestamp(CommandLogEntry entry) { return entry.getTimestamp(); }
    @Override protected String getServer(CommandLogEntry entry) { return entry.getServer(); }

    @Override
    protected Map<String, String> entryPlaceholders(CommandLogEntry entry,
                                                    String playerName, String timestamp, String server) {
        return mapOf(
            "timestamp", timestamp, "player", playerName, "server",
            server, "command", entry.getCommand() != null ? entry.getCommand() : "");
    }
}
