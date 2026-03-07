package gg.modl.minecraft.core.impl.commands.staff;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Syntax;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.AltsMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.Constants;
import gg.modl.minecraft.core.util.Pagination;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class AltsCommand extends BaseCommand {
    private static final String STATUS_COLOR_BANNED = "&c", STATUS_COLOR_MUTED = "&e", STATUS_COLOR_CLEAN = "&a";

    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_alts")
    @Syntax("<player> [-p [page]]")
    @Description("Open the alts menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void alts(CommandIssuer sender, @Name("player") String playerQuery, @Default() String flags) {
        int page = Pagination.parsePrintFlags(flags);
        boolean printMode = page > 0;

        if (!sender.isPlayer() || printMode) {
            printAlts(sender, playerQuery, Math.max(1, page));
            return;
        }

        UUID senderUuid = sender.getUniqueId();

        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

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
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager);
            return null;
        });
    }

    private void printAlts(CommandIssuer sender, String playerQuery, int page) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getLinkedAccounts(targetUuid).thenAccept(linkedResponse -> displayAlts(sender, playerName, linkedResponse.getLinkedAccounts(), page)).exceptionally(throwable -> {
                    if (throwable.getCause() instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
                    else displayAlts(sender, playerName, List.of(), page);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayAlts(CommandIssuer sender, String playerName, List<Account> linkedAccounts, int page) {
        sender.sendMessage(localeManager.getMessage("print.alts.header", Map.of("player", playerName)));

        if (linkedAccounts == null || linkedAccounts.isEmpty()) sender.sendMessage(localeManager.getMessage("print.alts.empty"));
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

                sender.sendMessage(localeManager.getMessage("print.alts.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "color", color,
                        "username", username,
                        "uuid", uuid,
                        "status", status
                )));
            }
            sender.sendMessage(localeManager.getMessage("print.alts.total", Map.of(
                    "count", String.valueOf(linkedAccounts.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.alts.footer"));
    }

}
