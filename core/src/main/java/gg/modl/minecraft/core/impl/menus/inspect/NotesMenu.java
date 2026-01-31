package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
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
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Notes Menu - displays and manages notes for a player.
 */
public class NotesMenu extends BaseInspectListMenu<Note> {

    private final Consumer<CirrusPlayerWrapper> backAction;

    /**
     * Create a new notes menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("Notes: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.NOTES;
    }

    private static String getPlayerNameStatic(Account account) {
        if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add create note button at slot 40 (y position in navigation row)
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
        // Notes are stored in the account object
        List<Note> notes = new ArrayList<>(targetAccount.getNotes());

        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (notes.isEmpty()) {
            return Collections.singletonList(new Note(null, new Date(), "", ""));
        }

        // Sort by date, newest first
        notes.sort((n1, n2) -> n2.getDate().compareTo(n1.getDate()));
        return notes;
    }

    @Override
    protected CirrusItem map(Note note) {
        LocaleManager locale = platform.getLocaleManager();

        // Handle placeholder for empty list
        if (note.getText() == null) {
            return createEmptyPlaceholder(locale.getMessage("menus.empty.notes"));
        }

        // Build variables map
        String formattedDate = MenuItems.formatDate(note.getDate());
        String author = note.getIssuerName() != null ? note.getIssuerName() : "Unknown";
        String content = note.getText();

        Map<String, String> vars = Map.of(
                "date", formattedDate,
                "author", author,
                "content", content
        );

        // Get lore from locale
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList("menus.note_item.lore")) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        // Get title from locale
        String title = locale.getMessage("menus.note_item.title", vars);

        return CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(title),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Note note) {
        // Handle placeholder - do nothing
        if (note.getText() == null) {
            return;
        }

        // Paste note content to player's chat input
        // Uses JSON component with suggest_command click event to put text in chat
        String noteText = note.getText();
        // Escape quotes for JSON
        String escapedText = noteText.replace("\\", "\\\\").replace("\"", "\\\"");

        // Create JSON component that suggests the note text as a command (puts in chat input)
        String json = "[{\"text\":\"" + MenuItems.COLOR_GRAY + "Note copied to chat: " + MenuItems.COLOR_WHITE +
                noteText.substring(0, Math.min(30, noteText.length())) +
                (noteText.length() > 30 ? "..." : "") +
                "\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"" + escapedText + "\"}}]";

        platform.sendJsonMessage(viewerUuid, json);
        sendMessage(MenuItems.COLOR_GREEN + "Click the message above to paste the note to your chat!");
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Create note handler
        registerActionHandler("createNote", this::handleCreateNote);

        // Override header navigation handlers - pass backAction to preserve the back button
        registerActionHandler("openNotes", click -> {
            // Already on notes, do nothing
        });
        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openHistory", ActionHandlers.openMenu(
                new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
    }

    private void handleCreateNote(Click click) {
        click.clickedMenu().close();

        // Prompt for note content in chat
        ChatInputManager.requestInput(platform, viewerUuid, "Enter note content for " + targetName + ":",
                input -> {
                    // Create the note via API
                    CreatePlayerNoteRequest request = new CreatePlayerNoteRequest(
                            targetUuid.toString(),
                            viewerName,
                            input
                    );

                    httpClient.createPlayerNote(request).thenAccept(v -> {
                        sendMessage(MenuItems.COLOR_GREEN + "Note created successfully!");
                        // Refresh the account and reopen the menu
                        httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
                            if (response.getStatus() == 200) {
                                platform.runOnMainThread(() -> {
                                    new NotesMenu(platform, httpClient, viewerUuid, viewerName,
                                            response.getProfile(), backAction)
                                            .display(click.player());
                                });
                            }
                        });
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to create note: " + e.getMessage());
                        return null;
                    });
                },
                () -> {
                    // Cancelled - reopen menu
                    sendMessage(MenuItems.COLOR_GRAY + "Note creation cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }
}
