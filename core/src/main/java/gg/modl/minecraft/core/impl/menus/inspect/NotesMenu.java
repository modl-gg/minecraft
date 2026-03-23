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
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectNavigationHandlers;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public class NotesMenu extends BaseInspectListMenu<Note> {
    private static final int PAGE_SIZE = 7;

    private final PaginatedDataSource<Note> dataSource;

    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("Notes: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        activeTab = InspectTab.NOTES;

        int totalCount = inspectContext != null ? inspectContext.noteCount() : targetAccount.getNotes().size();
        dataSource = new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<PaginatedDataSource.FetchResult<Note>> future = new CompletableFuture<>();
            httpClient.getPlayerNotes(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    future.complete(new PaginatedDataSource.FetchResult<>(response.getNotes(), response.getTotalCount()));
                } else {
                    future.complete(new PaginatedDataSource.FetchResult<>(listOf(), 0));
                }
            }).exceptionally(e -> {
                future.complete(new PaginatedDataSource.FetchResult<>(listOf(), totalCount));
                return null;
            });
            return future;
        });

        List<Note> initial = new ArrayList<>(targetAccount.getNotes());
        initial.sort((n1, n2) -> n2.getDate().compareTo(n1.getDate()));
        dataSource.initialize(initial, totalCount);
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
    protected boolean interceptNextPage(Click click) {
        int nextPage = currentPageIndex().get() + 1;
        if (!dataSource.isPageLoaded(nextPage)) {
            dataSource.setOnDataLoaded(() -> {
                NotesMenu newMenu = new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
                newMenu.dataSource.initialize(dataSource.getAllLoadedItems(), dataSource.getTotalCount());
                newMenu.display(click.player());
                newMenu.setInitialPage(nextPage);
                newMenu.display(click.player());
            });
            dataSource.fetchPage(dataSource.getAllLoadedItems().size() / PAGE_SIZE + 1);
            return true;
        }
        dataSource.prefetchIfNeeded(nextPage);
        return false;
    }

    @Override
    public boolean hasNextPage() {
        return currentPageIndex().get() < dataSource.getTotalMenuPages() - 1;
    }

    @Override
    protected Collection<Note> elements() {
        List<Note> notes = dataSource.getAllLoadedItems();
        if (notes.isEmpty())
            return Collections.singletonList(new Note(null, new Date(), "", ""));
        return notes;
    }

    @Override
    protected CirrusItem map(Note note) {
        LocaleManager locale = platform.getLocaleManager();

        if (note.getText() == null) return createEmptyPlaceholder(locale.getMessage("menus.empty.notes"));

        String formattedDate = MenuItems.formatDate(note.getDate());
        String author = note.getIssuerName() != null ? note.getIssuerName() : "";
        String content = note.getText();

        Map<String, String> vars = mapOf(
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
                this::registerActionHandler,
                platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        registerActionHandler("openNotes", click -> {});
    }

    private void handleCreateNote(Click click) {
        click.clickedMenu().close();

        platform.getChatInputManager().requestInput(viewerUuid, "Enter note content for " + targetName + ":",
                input -> {
                    String issuerId = platform.getCache() != null ? platform.getCache().getStaffId(viewerUuid) : null;
                    CreatePlayerNoteRequest request = new CreatePlayerNoteRequest(
                            targetUuid.toString(),
                            viewerName,
                            issuerId,
                            input
                    );

                    httpClient.createPlayerNote(request).thenAccept(v -> {
                        sendMessage(platform.getLocaleManager().getMessage("menus.notes.created"));
                        httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
                            if (response.getStatus() == 200) {
                                InspectContext newContext = new InspectContext(response.getProfile(),
                                        inspectContext != null ? inspectContext.punishmentCount() : response.getPunishmentCount(),
                                        response.getNoteCount());
                                new NotesMenu(platform, httpClient, viewerUuid, viewerName,
                                    response.getProfile(), backAction, newContext)
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
