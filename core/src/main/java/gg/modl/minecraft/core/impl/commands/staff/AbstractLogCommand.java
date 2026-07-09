package gg.modl.minecraft.core.impl.commands.staff;

import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.Pagination;
import gg.modl.minecraft.core.util.PermissionUtil;
import revxrsal.commands.command.CommandActor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public abstract class AbstractLogCommand<T> {
    private static final int ENTRIES_PER_PAGE = 10;

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

    protected void execute(CommandActor actor, String playerQuery, String pageArg) {
        if (!PermissionUtil.hasPermission(actor, cache, permission())) {
            actor.reply(localeManager.getMessage("general.no_permission"));
            return;
        }

        final int requestedPage = Pagination.parsePage(pageArg);
        String errorKey = localePrefix() + ".error";

        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        httpClientHolder.getClient().lookupPlayer(new PlayerLookupRequest(playerQuery)).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                String targetUuid = response.getData().getMinecraftUuid();

                fetchEntries(targetUuid).thenAccept(entries -> displayEntries(actor, playerName, entries, requestedPage)).exceptionally(throwable -> {
                    CommandUtil.handleException(actor, throwable, localeManager, errorKey);
                    return null;
                });
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager, errorKey);
            return null;
        });
    }

    private void displayEntries(CommandActor actor, String playerName, List<T> entries, int page) {
        String header = localeManager.getMessage(localePrefix() + ".header", mapOf("player", playerName));
        if (header != null && !header.isEmpty()) actor.reply(header);

        if (entries == null || entries.isEmpty()) {
            actor.reply(localeManager.getMessage(localePrefix() + ".empty"));
            return;
        }

        Pagination.Page pg = Pagination.paginate(entries, ENTRIES_PER_PAGE, page);

        for (int i = pg.getStart(); i < pg.getEnd(); i++) {
            T entry = entries.get(i);
            String timestamp = DateFormatter.format(new Date(getTimestamp(entry)));
            String server = getServer(entry) != null ? getServer(entry) : Constants.UNKNOWN;

            actor.reply(localeManager.getMessage(localePrefix() + ".entry",
                    entryPlaceholders(entry, playerName, timestamp, server)));
        }

        actor.reply(localeManager.getMessage(localePrefix() + ".footer", mapOf(
                "page", String.valueOf(pg.getPage()),
                "total_pages", String.valueOf(pg.getTotalPages()),
                "total", String.valueOf(entries.size())
        )));
    }
}
