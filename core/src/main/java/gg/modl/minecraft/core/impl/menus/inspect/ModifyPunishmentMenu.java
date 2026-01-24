package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PardonPunishmentRequest;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modify Punishment Menu - allows modifying an existing punishment.
 * Secondary menu accessed from History menu.
 */
public class ModifyPunishmentMenu extends BaseInspectMenu {

    private final Punishment punishment;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new modify punishment menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param punishment The punishment to modify
     * @param backAction Action to return to parent menu
     */
    public ModifyPunishmentMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                                 Account targetAccount, Punishment punishment, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.punishment = punishment;
        this.parentBackAction = backAction;

        title("Modify Punishment");
        activeTab = InspectTab.HISTORY;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        String typeName = punishment.getType() != null ? punishment.getType().name() : "Unknown";
        boolean isBanType = punishment.getType() == Punishment.Type.BAN ||
                            punishment.getType() == Punishment.Type.SECURITY_BAN ||
                            punishment.getType() == Punishment.Type.LINKED_BAN ||
                            punishment.getType() == Punishment.Type.BLACKLIST;

        // Slot 28: Add Note
        set(CirrusItem.of(
                ItemType.OAK_SIGN,
                ChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Add Note"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Add a staff note to this punishment"
                )
        ).slot(MenuSlots.MODIFY_ADD_NOTE).actionHandler("addNote"));

        // Slot 29: Evidence
        List<String> evidenceLore = new ArrayList<>();
        evidenceLore.add(MenuItems.COLOR_GRAY + "Current evidence:");
        // TODO: Fetch evidence from punishment data when endpoint available
        evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "(No evidence attached)");
        evidenceLore.add("");
        evidenceLore.add(MenuItems.COLOR_YELLOW + "Left-click to add evidence");
        evidenceLore.add(MenuItems.COLOR_YELLOW + "Right-click to view in chat");

        set(CirrusItem.of(
                ItemType.ARROW,
                ChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Evidence"),
                MenuItems.lore(evidenceLore)
        ).slot(MenuSlots.MODIFY_EVIDENCE).actionHandler("evidence"));

        // Slot 30: Pardon Punishment
        set(CirrusItem.of(
                ItemType.GOLDEN_APPLE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Pardon Punishment"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Remove punishment and clear",
                        MenuItems.COLOR_GRAY + "associated points"
                )
        ).slot(MenuSlots.MODIFY_PARDON).actionHandler("pardon"));

        // Slot 31: Change Duration
        set(CirrusItem.of(
                ItemType.ANVIL,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Change Duration"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Shorten or lengthen punishment duration",
                        "",
                        MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(punishment.getDuration())
                )
        ).slot(MenuSlots.MODIFY_DURATION).actionHandler("changeDuration"));

        // Slot 33: Toggle Stat-Wipe (ban types only)
        if (isBanType) {
            // TODO: Get actual stat-wipe status from punishment data
            boolean statWipe = false;
            set(CirrusItem.of(
                    statWipe ? ItemType.EXPERIENCE_BOTTLE : ItemType.GLASS_BOTTLE,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Stat-Wipe"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (statWipe ? "Disable" : "Enable") + " stat-wiping for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_STAT_WIPE).actionHandler("toggleStatWipe"));

            // Slot 34: Toggle Alt-Blocking
            // TODO: Get actual alt-blocking status from punishment data
            boolean altBlock = false;
            set(CirrusItem.of(
                    altBlock ? ItemType.TORCH : ItemType.REDSTONE_TORCH,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Alt-Blocking"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (altBlock ? "Disable" : "Enable") + " alt-blocking for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_ALT_BLOCK).actionHandler("toggleAltBlock"));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation to return to history
        registerActionHandler("openHistory", click -> {
            click.clickedMenu().close();
            new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
        });

        registerActionHandler("openNotes", click -> {
            click.clickedMenu().close();
            new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
        });

        registerActionHandler("openAlts", click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
        });

        registerActionHandler("openReports", click -> {
            click.clickedMenu().close();
            new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
        });

        registerActionHandler("openPunish", click -> {
            click.clickedMenu().close();
            new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
        });

        // Add note handler
        registerActionHandler("addNote", this::handleAddNote);

        // Evidence handler
        registerActionHandler("evidence", this::handleEvidence);

        // Pardon handler
        registerActionHandler("pardon", this::handlePardon);

        // Change duration handler
        registerActionHandler("changeDuration", this::handleChangeDuration);

        // Toggle stat-wipe handler
        registerActionHandler("toggleStatWipe", this::handleToggleStatWipe);

        // Toggle alt-blocking handler
        registerActionHandler("toggleAltBlock", this::handleToggleAltBlock);
    }

    private void handleAddNote(Click click) {
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid, "Enter note to add to this punishment:",
                input -> {
                    // TODO: Implement endpoint POST /v1/panel/punishments/{id}/notes
                    sendMessage(MenuItems.COLOR_YELLOW + "Note adding not yet implemented - endpoint needed");
                    sendMessage(MenuItems.COLOR_GRAY + "Note text: " + input);
                    platform.runOnMainThread(() -> display(click.player()));
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Note cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handleEvidence(Click click) {
        // Check click type
        // For now, just show add evidence prompt
        click.clickedMenu().close();

        sendMessage("");
        sendMessage(MenuItems.COLOR_GOLD + "Evidence Options:");
        sendMessage(MenuItems.COLOR_AQUA + "[Add Evidence by Link]" + MenuItems.COLOR_GRAY + " - Click to add a link");
        sendMessage(MenuItems.COLOR_AQUA + "[Add Evidence by File Upload]" + MenuItems.COLOR_GRAY + " - Coming soon");
        sendMessage("");

        ChatInputManager.requestInput(platform, viewerUuid, "Enter evidence URL:",
                input -> {
                    // TODO: Implement endpoint POST /v1/panel/punishments/{id}/evidence
                    sendMessage(MenuItems.COLOR_YELLOW + "Evidence adding not yet implemented - endpoint needed");
                    sendMessage(MenuItems.COLOR_GRAY + "URL: " + input);
                    platform.runOnMainThread(() -> display(click.player()));
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Evidence cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handlePardon(Click click) {
        PardonPunishmentRequest request = new PardonPunishmentRequest(
                punishment.getId(),
                viewerName,
                "Pardoned via GUI",
                null // expectedType
        );

        httpClient.pardonPunishment(request).thenAccept(v -> {
            sendMessage(MenuItems.COLOR_GREEN + "Punishment pardoned successfully!");
            // Return to history menu
            platform.runOnMainThread(() -> {
                click.clickedMenu().close();
                httpClient.getPlayerProfile(targetUuid).thenAccept(response -> {
                    if (response.getStatus() == 200) {
                        platform.runOnMainThread(() -> {
                            new HistoryMenu(platform, httpClient, viewerUuid, viewerName,
                                    response.getProfile(), parentBackAction)
                                    .display(click.player());
                        });
                    }
                });
            });
        }).exceptionally(e -> {
            sendMessage(MenuItems.COLOR_RED + "Failed to pardon punishment: " + e.getMessage());
            return null;
        });
    }

    private void handleChangeDuration(Click click) {
        click.clickedMenu().close();

        ChatInputManager.requestInput(platform, viewerUuid,
                "Enter new duration (e.g., 30d2h3m4s, or 'perm' for permanent):",
                input -> {
                    // TODO: Implement endpoint PATCH /v1/panel/punishments/{id}
                    sendMessage(MenuItems.COLOR_YELLOW + "Duration change not yet implemented - endpoint needed");
                    sendMessage(MenuItems.COLOR_GRAY + "New duration: " + input);
                    platform.runOnMainThread(() -> display(click.player()));
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Duration change cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handleToggleStatWipe(Click click) {
        // TODO: Implement endpoint PATCH /v1/panel/punishments/{id}
        sendMessage(MenuItems.COLOR_YELLOW + "Stat-wipe toggle not yet implemented - endpoint needed");
    }

    private void handleToggleAltBlock(Click click) {
        // TODO: Implement endpoint PATCH /v1/panel/punishments/{id}
        sendMessage(MenuItems.COLOR_YELLOW + "Alt-blocking toggle not yet implemented - endpoint needed");
    }
}
