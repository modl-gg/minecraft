package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.util.Permissions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@CommandAlias("%cmd_commandlogs") @Conditions("staff")
public class CommandLogsCommand extends AbstractLogCommand<ChatCommandLogService.CommandLogEntry> {
    private final ChatCommandLogService logService;

    public CommandLogsCommand(HttpClientHolder httpClientHolder, ChatCommandLogService logService, Cache cache, LocaleManager localeManager) {
        super(httpClientHolder, cache, localeManager);
        this.logService = logService;
    }

    @CommandCompletion("@players")
    @Default
    @Syntax("<player> [page]")
    @Description("View recent commands for a player")
    public void commandLogs(CommandIssuer sender, @Name("player") String playerQuery, @Default("1") String pageArg) {
        execute(sender, playerQuery, pageArg);
    }

    @Override protected String permission() { return Permissions.COMMAND_LOGS; }
    @Override protected String localePrefix() { return "command_logs"; }

    @Override
    protected CompletableFuture<List<ChatCommandLogService.CommandLogEntry>> fetchEntries(String uuid) {
        return logService.getCommandLogs(httpClientHolder, uuid, 200);
    }

    @Override protected long getTimestamp(ChatCommandLogService.CommandLogEntry entry) { return entry.getTimestamp(); }
    @Override protected String getServer(ChatCommandLogService.CommandLogEntry entry) { return entry.getServer(); }

    @Override
    protected Map<String, String> entryPlaceholders(ChatCommandLogService.CommandLogEntry entry,
                                                    String playerName, String timestamp, String server) {
        return Map.of(
            "timestamp", timestamp, "player", playerName, "server",
            server, "command", entry.getCommand() != null ? entry.getCommand() : "");
    }
}
