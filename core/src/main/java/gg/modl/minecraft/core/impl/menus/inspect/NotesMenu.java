package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class NotesMenu extends BaseInspectListMenu<Note> {
    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Notes: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        activeTab = InspectTab.NOTES;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        items.put(MenuSlots.CREATE_NOTE_BUTTON, CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Create Note"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Add a new note for " + targetName
                )
        ).actionHandler("createNote"));

        return items;
    }

    @Override
    protected Collection<Note> elements() {
        List<Note> notes = new ArrayList<>(targetAccount.getNotes());
        if (notes.isEmpty())
            return Collections.singletonList(new Note(null, new Date(), "", ""));
        notes.sort((n1, n2) -> n2.getDate().compareTo(n1.getDate()));
        return notes;
    }

    @Override
    protected CirrusItem map(Note note) {
        LocaleManager locale = platform.getLocaleManager();

        if (note.getText() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.notes"));

        String formattedDate = MenuItems.formatDate(note.getDate());
        String author = note.getIssuerName() != null ? note.getIssuerName() : "Unknown";
        String content = note.getText();

        Map<String, String> vars = Map.of(
                "date", formattedDate,
                "author", author,
                "content", content
        );

        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.note_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        String title = locale.getMessage("menus.note_item.title", vars);

        return CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Note note) {
        if (note.getText() == null) return;

        String noteText = note.getText();
        String escapedText = noteText.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "[{\"text\":\"" + MenuItems.COLOR_GRAY + "Note: " + MenuItems.COLOR_WHITE +
                noteText.substring(0, Math.min(30, noteText.length())) +
                (noteText.length() > 30 ? "..." : "") +
                "\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"" + escapedText + "\"}}]";

        platform.sendJsonMessage(viewerUuid, json);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("createNote", this::handleCreateNote);
        InspectNavigationHandlers.registerAll(
                (name, handler) -> registerActionHandler(name, handler),
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        registerActionHandler("openNotes", click -> {});
    }

    private void handleCreateNote(Click click) {
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid, "Enter note content for " + targetName + ":",
                input -> {
                    CreatePlayerNoteRequest request = new CreatePlayerNoteRequest(
                            targetUuid.toString(),
                            viewerName,
                            input
                    );

                    httpClient.createPlayerNote(request).thenAccept(v -> {
                        sendMessage(platform.getLocaleManager().getMessage("menus.notes.created"));
                        httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
                            if (response.getStatus() == 200) {
                                new NotesMenu(platform, httpClient, viewerUuid, viewerName,
                                    response.getProfile(), backAction)
                                    .display(click.player());
                            }
                        });
                    }).exceptionally(e -> {
                        sendMessage(platform.getLocaleManager().getMessage("menus.notes.create_failed"));
                        return null;
                    });
                },
                () -> {
                    sendMessage(platform.getLocaleManager().getMessage("menus.notes.cancelled"));
                    display(click.player());
                }
        );
    }
}
