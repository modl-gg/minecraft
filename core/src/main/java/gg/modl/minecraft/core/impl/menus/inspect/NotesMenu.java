package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Note;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.CreatePlayerNoteRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.Collection;
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

        // Add create note button in slot 53
        items.put(MenuSlots.CREATE_BUTTON, CirrusItem.of(
                ItemType.WRITABLE_BOOK,
                ChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Create Note"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Add a new note for " + targetName
                )
        ).actionHandler("createNote"));

        return items;
    }

    @Override
    protected Collection<Note> elements() {
        // Notes are stored in the account object
        // Sort by date, newest first
        List<Note> notes = new ArrayList<>(targetAccount.getNotes());
        notes.sort((n1, n2) -> n2.getDate().compareTo(n1.getDate()));
        return notes;
    }

    @Override
    protected CirrusItem map(Note note) {
        List<String> lore = new ArrayList<>();
        lore.add(MenuItems.COLOR_GRAY + "By: " + MenuItems.COLOR_WHITE + note.getIssuerName());
        lore.add("");
        // Split content into lines (7 words per line)
        lore.addAll(MenuItems.wrapText(note.getText(), 7));

        return CirrusItem.of(
                ItemType.PAPER,
                ChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + MenuItems.formatDate(note.getDate())),
                MenuItems.lore(lore)
        );
    }

    @Override
    protected void handleClick(Click click, Note note) {
        // Notes are view-only, no action on click
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Create note handler
        registerActionHandler("createNote", this::handleCreateNote);

        // Override header navigation handlers
        registerActionHandler("openNotes", click -> {
            // Already on notes, do nothing
        });
        registerActionHandler("openAlts", click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openHistory", click -> {
            click.clickedMenu().close();
            new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openReports", click -> {
            click.clickedMenu().close();
            new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
        registerActionHandler("openPunish", click -> {
            click.clickedMenu().close();
            new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)
                    .display(click.player());
        });
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
