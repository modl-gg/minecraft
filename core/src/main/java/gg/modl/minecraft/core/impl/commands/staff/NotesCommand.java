package gg.modl.minecraft.core.impl.commands.staff;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.request.PlayerLookupRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.command.PlayerOnly;
import gg.modl.minecraft.core.command.StaffOnly;
import gg.modl.minecraft.core.impl.menus.inspect.NotesMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.DateFormatter;
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
public class NotesCommand {
    private final HttpClientHolder httpClientHolder;
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;

    @Command("notes")
    @Description("Open the notes menu for a player, or use -p to print to chat")
    @PlayerOnly @StaffOnly
    public void notes(CommandActor actor, @Named("player") String playerQuery, @revxrsal.commands.annotation.Optional String flags) {
        if (flags == null) flags = "";
        int page = Pagination.parsePrintFlags(flags);
        boolean printMode = page > 0;

        if (actor.uniqueId() == null || printMode) {
            printNotes(actor, playerQuery, Math.max(1, page));
            return;
        }

        UUID senderUuid = actor.uniqueId();
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);
        httpClientHolder.getClient().lookupPlayerProfile(request).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                String senderName = CommandUtil.resolveSenderName(senderUuid, cache, platform);
                NotesMenu menu = new NotesMenu(
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

    private void printNotes(CommandActor actor, String playerQuery, int page) {
        actor.reply(localeManager.getMessage("player_lookup.looking_up", mapOf("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayerProfile(request).thenAccept(profileResponse -> {
            if (profileResponse.getStatus() == 200) {
                Account profile = profileResponse.getProfile();
                List<Account.Username> usernames = profile.getUsernames();
                String playerName = !usernames.isEmpty() ? usernames.get(usernames.size() - 1).getUsername() : playerQuery;
                displayNotes(actor, playerName, profile, page);
            } else actor.reply(localeManager.getMessage("general.player_not_found"));
        }).exceptionally(throwable -> {
            CommandUtil.handleException(actor, throwable, localeManager);
            return null;
        });
    }

    private static final int ENTRIES_PER_PAGE = 8;

    private void displayNotes(CommandActor actor, String playerName, Account profile, int page) {
        List<Note> notes = profile.getNotes();
        actor.reply(localeManager.getMessage("print.notes.header", mapOf("player", playerName)));

        if (notes.isEmpty()) actor.reply(localeManager.getMessage("print.notes.empty"));
        else {
            Pagination.Page pg = Pagination.paginate(notes, ENTRIES_PER_PAGE, page);
            for (int i = pg.getStart(); i < pg.getEnd(); i++) {
                int ordinal = i + 1;
                Note note = notes.get(i);
                String date = DateFormatter.format(note.getDate());
                String author = note.getIssuerName();
                String content = note.getText();

                actor.reply(localeManager.getMessage("print.notes.entry", mapOf(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "author", author,
                        "content", content
                )));
            }
            actor.reply(localeManager.getMessage("print.notes.total", mapOf(
                    "count", String.valueOf(notes.size()),
                    "page", String.valueOf(pg.getPage()),
                    "total_pages", String.valueOf(pg.getTotalPages())
            )));
        }

        actor.reply(localeManager.getMessage("print.notes.footer"));
    }

}
