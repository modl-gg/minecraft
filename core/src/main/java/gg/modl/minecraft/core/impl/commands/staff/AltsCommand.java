package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.AltsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.Pagination;
import lombok.RequiredArgsConstructor;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.command.CommandActor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

@RequiredArgsConstructor
public class AltsCommand {
    private static final String STATUS_COLOR_BANNED = "&c", STATUS_COLOR_MUTED = "&e", STATUS_COLOR_CLEAN = "&a";

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Command("alts")
    @Description("Open the alts menu for a player, or use -p to print to chat")
    @PlayerOnly @StaffOnly
    public void alts(CommandActor actor, @Named("player") String playerQuery, @revxrsal.commands.annotation.Optional String flags) {
        if (flags == null) flags = "";
        int page = Pagination.parsePrintFlags(flags);
        boolean printMode = page > 0;

        if (actor.uniqueId() == null || printMode) {
            printAlts(actor, playerQuery, Math.max(1, page));
            return;
        }

        UUID senderUuid = actor.uniqueId();

        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        httpClientHolder.getClient().lookupPlayerProfile(request).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                AltsMenu menu = new AltsMenu(
                    platform, httpClientHolder.getClient(), senderUuid, senderName,
                    profileResponse.getProfile(), null
                );
                CirrusPlayerWrapper player = platform.getPlayerWrapper(senderUuid);
                menu.display(player);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private void printAlts(CommandActor actor, String playerQuery, int page) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getLinkedAccounts(targetUuid).thenAccept(linkedResponse -> displayAlts(actor, playerName, linkedResponse.getLinkedAccounts(), page)).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) actor.reply(localeManager.getMessage("api_errors.panel_restarting"));
                    else displayAlts(actor, playerName, listOf(), page);
                    return null;
                });
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayAlts(CommandActor actor, String playerName, List<Account> linkedAccounts, int page) {
        actor.reply(localeManager.getMessage("print.alts.header", mapOf("player", playerName)));

        if (linkedAccounts == null || linkedAccounts.isEmpty()) actor.reply(localeManager.getMessage("print.alts.empty"));
        else {
            Pagination.Page pg = Pagination.paginate(linkedAccounts, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                int ordinal = i + 1;
                Account account = linkedAccounts.get(i);
                String username = Constants.UNKNOWN;
                if (!account.getUsernames().isEmpty())
                    username = account.getUsernames().get(account.getUsernames().size() - 1).getUsername();

                String uuid = account.getMinecraftUuid() != null ? account.getMinecraftUuid().toString() : Constants.UNKNOWN;
                CachedProfile altProfile = account.getMinecraftUuid() != null ? cache.getPlayerProfile(account.getMinecraftUuid()) : null;
                boolean isBanned = altProfile != null && altProfile.isBanned();
                boolean isMuted = altProfile != null && altProfile.isMuted();

                String status;
                if (isBanned && isMuted) status = localeManager.getMessage("print.alts.status_banned_and_muted");
                else if (isBanned) status = localeManager.getMessage("print.alts.status_banned");
                else if (isMuted) status = localeManager.getMessage("print.alts.status_muted");
                else status = localeManager.getMessage("print.alts.status_clean");

                String color = isBanned ? STATUS_COLOR_BANNED : (isMuted ? STATUS_COLOR_MUTED : STATUS_COLOR_CLEAN);

                actor.reply(localeManager.getMessage("print.alts.entry", mapOf(
                        "ordinal", String.valueOf(ordinal),
                        "color", color,
                        "username", username,
                        "uuid", uuid,
                        "status", status
                )));
            }
            actor.reply(localeManager.getMessage("print.alts.total", mapOf(
                    "count", String.valueOf(linkedAccounts.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        actor.reply(localeManager.getMessage("print.alts.footer"));
    }

}
