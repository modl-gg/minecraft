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
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.NotesMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.DateFormatter;
import gg.modl.minecraft.core.util.Pagination;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class NotesCommand extends BaseCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @CommandCompletion("@players")
    @CommandAlias("%cmd_notes")
    @Syntax("<player> [-p [page]]")
    @Description("Open the notes menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void notes(CommandIssuer sender, @Name("player") String playerQuery, @Default() String flags) {
        int page = Pagination.parsePrintFlags(flags);
        boolean printMode = page > 0;

        if (!sender.isPlayer() || printMode) {
            printNotes(sender, playerQuery, Math.max(1, page));
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200) {
                        String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                        NotesMenu menu = new NotesMenu(
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
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager);
            return null;
        });
    }

    private void printNotes(CommandIssuer sender, String playerQuery, int page) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200) {
                        Account profile = profileResponse.getProfile();
                        displayNotes(sender, playerName, profile, page);
                    } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
                }).exceptionally(throwable -> {
                    CommandUtil.handleException(sender, throwable, localeManager);
                    return null;
                });
            } else sender.sendMessage(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(sender, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayNotes(CommandIssuer sender, String playerName, Account profile, int page) {
        List<Note> notes = profile.getNotes();
        sender.sendMessage(localeManager.getMessage("print.notes.header", Map.of("player", playerName)));

        if (notes.isEmpty()) sender.sendMessage(localeManager.getMessage("print.notes.empty"));
        else {
            Pagination.Page pg = Pagination.paginate(notes, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                int ordinal = i + 1;
                Note note = notes.get(i);
                String date = DateFormatter.format(note.getDate());
                String author = note.getIssuerName();
                String content = note.getText();

                sender.sendMessage(localeManager.getMessage("print.notes.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "author", author,
                        "content", content
                )));
            }
            sender.sendMessage(localeManager.getMessage("print.notes.total", Map.of(
                    "count", String.valueOf(notes.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.notes.footer"));
    }

}
