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
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.inspect.NotesMenu;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.CommandUtil;
import gg.modl.minecraft.core.util.DateFormatter;
import lombok.RequiredArgsConstructor;

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
    @Syntax("<player> [-p]")
    @Description("Open the notes menu for a player, or use -p to print to chat")
    @Conditions("player|staff")
    public void notes(CommandIssuer sender, @Name("player") String playerQuery, @Default() String flags) {
        boolean printMode = flags.equalsIgnoreCase("-p") || flags.equalsIgnoreCase("print");

        if (!sender.isPlayer() || printMode) {
            printNotes(sender, playerQuery);
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

    private void printNotes(CommandIssuer sender, String playerQuery) {
        sender.sendMessage(localeManager.getMessage("player_lookup.looking_up", Map.of("player", playerQuery)));

        PlayerLookupRequest request = new PlayerLookupRequest(playerQuery);

        httpClientHolder.getClient().lookupPlayer(request).thenAccept(response -> {
            if (response.isSuccess() && response.getData() != null) {
                String playerName = response.getData().getCurrentUsername();
                UUID targetUuid = UUID.fromString(response.getData().getMinecraftUuid());

                httpClientHolder.getClient().getPlayerProfile(targetUuid).thenAccept(profileResponse -> {
                    if (profileResponse.getStatus() == 200) {
                        Account profile = profileResponse.getProfile();
                        displayNotes(sender, playerName, profile);
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

    private void displayNotes(CommandIssuer sender, String playerName, Account profile) {
        sender.sendMessage(localeManager.getMessage("print.notes.header", Map.of("player", playerName)));

        if (profile.getNotes().isEmpty()) sender.sendMessage(localeManager.getMessage("print.notes.empty"));
        else {
            int ordinal = 1;
            for (Note note : profile.getNotes()) {
                String date = DateFormatter.format(note.getDate());
                String author = note.getIssuerName();
                String content = note.getText();

                sender.sendMessage(localeManager.getMessage("print.notes.entry", Map.of(
                        "ordinal", String.valueOf(ordinal),
                        "date", date,
                        "author", author,
                        "content", content
                )));
                ordinal++;
            }
            sender.sendMessage(localeManager.getMessage("print.notes.total", Map.of(
                    "count", String.valueOf(profile.getNotes().size())
            )));
        }

        sender.sendMessage(localeManager.getMessage("print.notes.footer"));
    }

}
