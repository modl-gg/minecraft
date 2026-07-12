package gg.modl.minecraft.core.impl.menus.inspect;

import gg.modl.minecraft.core.PluginServices;
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
import gg.modl.minecraft.core.impl.menus.base.PaginatedInspectListMenu;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource;
import gg.modl.minecraft.core.impl.menus.pagination.PaginatedDataSource.FetchResult;
import gg.modl.minecraft.core.impl.menus.util.InspectContext;
import gg.modl.minecraft.core.impl.menus.util.InspectTabItems.InspectTab;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NotesMenu extends PaginatedInspectListMenu<Note> {
    private static final int PAGE_SIZE = 7;

    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        this(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, null);
    }

    public NotesMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                     Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, InspectContext inspectContext) {
        super("Notes: " + ReportRenderUtil.getPlayerName(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext, PAGE_SIZE);
        activeTab = InspectTab.NOTES;

        int totalCount = inspectContext != null ? inspectContext.noteCount() : targetAccount.getNotes().size();
        dataSource = new PaginatedDataSource<>(PAGE_SIZE, (page, limit) -> {
            CompletableFuture<FetchResult<Note>> future = new CompletableFuture<>();
            httpClient.getPlayerNotes(targetUuid, page, limit).thenAccept(response -> {
                if (response.getStatus() == 200) {
                    future.complete(new FetchResult<>(response.getNotes(), response.getTotalCount()));
                } else {
                    future.complete(new FetchResult<>(listOf(), 0, false));
                }
            }).exceptionally(e -> {
                future.complete(new FetchResult<>(listOf(), totalCount, false));
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
    protected void openLoadedPage(Click click, int nextPage) {
        NotesMenu newMenu = new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction, inspectContext);
        newMenu.dataSource.initialize(dataSource.getAllLoadedItems(), dataSource.getTotalCount());
        newMenu.setInitialPage(nextPage);
        newMenu.display(click.player());
    }

    @Override
    protected Note emptyElement() {
        return new Note(null, new Date(), "", "");
    }

    @Override
    protected CirrusItem map(Note note) {
        LocaleManager locale = PluginServices.locale();

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
        String preview = noteText.substring(0, Math.min(30, noteText.length())) +
                (noteText.length() > 30 ? "..." : "");
        String escapedPreview = escapeJson(preview);
        String escapedText = escapeJson(noteText);
        String json = "{\"text\":\"\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"" + escapedText +
                "\"},\"extra\":[" +
                "{\"text\":\"Note: \",\"color\":\"gray\"}," +
                "{\"text\":\"" + escapedPreview + "\",\"color\":\"white\"}" +
                "]}";

        platform.sendJsonMessage(viewerUuid, json);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        registerActionHandler("createNote", this::handleCreateNote);
        registerActionHandler("openNotes", click -> {});
    }

    private void handleCreateNote(Click click) {
        click.clickedMenu().close();

        PluginServices.chatInput().requestInput(viewerUuid, "Enter note content for " + targetName + ":",
                input -> {
                    String issuerId = PluginServices.cache() != null ? PluginServices.cache().getStaffId(viewerUuid) : null;
                    CreatePlayerNoteRequest request = new CreatePlayerNoteRequest(
                            targetUuid.toString(),
                            viewerName,
                            issuerId,
                            input
                    );

                    httpClient.createPlayerNote(request).thenAccept(v -> {
                        sendMessage(PluginServices.locale().getMessage("menus.notes.created"));
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
                        sendMessage(PluginServices.locale().getMessage("menus.notes.create_failed"));
                        return null;
                    });
                },
                () -> {
                    sendMessage(PluginServices.locale().getMessage("menus.notes.cancelled"));
                    display(click.player());
                }
        );
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
