package gg.modl.minecraft.core.impl.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.PermissionUtil;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to view a player's recent chat messages.
 * Fetches the last 200 chat messages from the backend and displays them paginated.
 */
@RequiredArgsConstructor
@CommandAlias("%cmd_chatlogs")
@Conditions("staff")
public class ChatLogsCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final ChatCommandLogService logService;
    private final Cache cache;
    private final LocaleManager localeManager;

    private static final int ENTRIES_PER_PAGE = 10;
    private static final int MAX_ENTRIES = 200;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @CommandCompletion("@players")
    @Default
    @Syntax("<player> [page]")
    @Description("View recent chat messages for a player")
    public void chatLogs(CommandIssuer sender, @Name("player") String playerQuery, @Default("1") String pageArg) {
        if (!PermissionUtil.hasPermission(sender, cache, "staff.chatlogs")) {
            sender.sendMessage(localeManager.getMessage("general.no_permission"));
            return;
        }

        int page;
        try {
            page = Integer.parseInt(pageArg);
        } catch (NumberFormatException e) {
            page = 1;
        }
        if (page < 1) page = 1;

        final int requestedPage = page;

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        getHttpClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                String targetUuid = response.getData().getMinecraftUuid();

                logService.getChatLogs(httpClientHolder, targetUuid, MAX_ENTRIES).thenAccept(entries -> {
                    displayChatLogs(sender, playerName, entries, requestedPage);
                }).exceptionally(throwable -> {
                    handleException(sender, throwable, playerQuery);
                    return null;
                });
            } else {
                sender.sendMessage(localeManager.getMessage("general.player_not_found"));
            }
        }).exceptionally(throwable -> {
            handleException(sender, throwable, playerQuery);
            return null;
        });
    }

    private void displayChatLogs(CommandIssuer sender, String playerName, List<ChatCommandLogService.ChatLogEntry> entries, int page) {
        String header = localeManager.getMessage("chat_logs.header", Map.of("player", playerName));
        if (header != null && !header.isEmpty()) {
            sender.sendMessage(header);
        }

        if (entries == null || entries.isEmpty()) {
            sender.sendMessage(localeManager.getMessage("chat_logs.empty"));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        if (page > totalPages) {
            page = totalPages;
        }

        int start = (page - 1) * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            ChatCommandLogService.ChatLogEntry entry = entries.get(i);
            String timestamp = DateFormatter.format(new Date(entry.getTimestamp()));
            String server = entry.getServer() != null ? entry.getServer() : "Unknown";
            String message = entry.getMessage() != null ? entry.getMessage() : "";

            sender.sendMessage(localeManager.getMessage("chat_logs.entry", Map.of(
                    "timestamp", timestamp,
                    "server", server,
                    "message", message
            )));
        }

        sender.sendMessage(localeManager.getMessage("chat_logs.footer", Map.of(
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages),
                "total", String.valueOf(entries.size())
        )));
    }

    private void handleException(CommandIssuer sender, Throwable throwable, String playerQuery) {
        if (throwable.getCause() instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage("chat_logs.error"));
        }
    }
}
