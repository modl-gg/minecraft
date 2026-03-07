package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.PermissionUtil;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractLogCommand<T> extends BaseCommand {
    private static final int ENTRIES_PER_PAGE = 10, MAX_ENTRIES = 200;

    protected final HttpClientHolder httpClientHolder;
    protected final Cache cache;
    protected final LocaleManager localeManager;

    protected AbstractLogCommand(HttpClientHolder httpClientHolder, Cache cache, LocaleManager localeManager) {
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.localeManager = localeManager;
    }

    protected abstract String permission();
    protected abstract String localePrefix();
    protected abstract CompletableFuture<List<T>> fetchEntries(String uuid);
    protected abstract long getTimestamp(T entry);
    protected abstract String getServer(T entry);
    protected abstract Map<String, String> entryPlaceholders(T entry, String playerName, String timestamp, String server);

    protected void execute(CommandIssuer sender, String playerQuery, String pageArg) {
        if (!PermissionUtil.hasPermission(sender, cache, permission())) {
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
        String errorKey = localePrefix() + ".error";

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        httpClientHolder.getClient().lookupPlayer(new PlayerLookupRequest(playerQuery)).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                String targetUuid = response.getData().getMinecraftUuid();

                fetchEntries(targetUuid).thenAccept(entries -> displayEntries(sender, playerName, entries, requestedPage)).exceptionally(throwable -> {
                    CommandUtil.handleException(sender, throwable, localeManager, errorKey);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager, errorKey);
            return null;
        });
    }

    private void displayEntries(CommandIssuer sender, String playerName, List<T> entries, int page) {
        String header = localeManager.getMessage(localePrefix() + ".header", Map.of("player", playerName));
        if (header != null && !header.isEmpty()) sender.sendMessage(header);

        if (entries == null || entries.isEmpty()) {
            sender.sendMessage(localeManager.getMessage(localePrefix() + ".empty"));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            T entry = entries.get(i);
            String timestamp = DateFormatter.format(new Date(getTimestamp(entry)));
            String server = getServer(entry) != null ? getServer(entry) : Constants.UNKNOWN;

            sender.sendMessage(localeManager.getMessage(localePrefix() + ".entry",
                    entryPlaceholders(entry, playerName, timestamp, server)));
        }

        sender.sendMessage(localeManager.getMessage(localePrefix() + ".footer", Map.of(
                "page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages),
                "total", String.valueOf(entries.size())
        )));
    }
}
