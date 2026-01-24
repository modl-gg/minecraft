package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandler;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentCreateRequest;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
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
 * Punish Severity Menu - select severity level for a punishment.
 * Secondary menu accessed from PunishMenu.
 */
public class PunishSeverityMenu extends BaseInspectMenu {

    private final PunishmentTypesResponse.PunishmentTypeData punishmentType;
    private final Consumer<CirrusPlayerWrapper> parentBackAction;
    private boolean silentMode = false;
    private boolean altBlocking = false;
    private boolean statWipe = false;

    /**
     * Create a new severity menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param punishmentType The punishment type to issue
     * @param backAction Action to return to parent menu
     */
    public PunishSeverityMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                               Account targetAccount, PunishmentTypesResponse.PunishmentTypeData punishmentType,
                               Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.punishmentType = punishmentType;
        this.parentBackAction = backAction;

        title("Punish: " + punishmentType.getName());
        activeTab = InspectTab.PUNISH;
        buildMenu();
    }

    private void buildMenu() {
        buildHeader();

        boolean isBanType = punishmentType.getCategory() != null &&
                (punishmentType.getCategory().toLowerCase().contains("ban") ||
                 punishmentType.getCategory().toLowerCase().contains("security"));

        // Slot 28: Lenient (lime wool)
        set(CirrusItem.of(
                ItemType.LIME_WOOL,
                ChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Lenient"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue a lenient punishment",
                        "",
                        MenuItems.COLOR_GRAY + "Click to issue " + (silentMode ? "silent " : "public ") + "punishment"
                )
        ).slot(MenuSlots.SEVERITY_LENIENT).actionHandler("issueLenient"));

        // Slot 30: Regular (yellow wool)
        set(CirrusItem.of(
                ItemType.YELLOW_WOOL,
                ChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Regular"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue a regular punishment",
                        "",
                        MenuItems.COLOR_GRAY + "Click to issue " + (silentMode ? "silent " : "public ") + "punishment"
                )
        ).slot(MenuSlots.SEVERITY_REGULAR).actionHandler("issueRegular"));

        // Slot 32: Aggravated (red wool)
        set(CirrusItem.of(
                ItemType.RED_WOOL,
                ChatElement.ofLegacyText(MenuItems.COLOR_RED + "Aggravated"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue an aggravated punishment",
                        "",
                        MenuItems.COLOR_GRAY + "Click to issue " + (silentMode ? "silent " : "public ") + "punishment"
                )
        ).slot(MenuSlots.SEVERITY_AGGRAVATED).actionHandler("issueAggravated"));

        // Slot 33: Silent Mode toggle
        set(CirrusItem.of(
                silentMode ? ItemType.LIME_DYE : ItemType.GRAY_DYE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Silent Mode: " + (silentMode ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Toggle silent mode for this punishment"
                )
        ).slot(MenuSlots.SEVERITY_SILENT).actionHandler("toggleSilent"));

        // Slot 42: Alt-Blocking toggle (ban types only)
        if (isBanType && punishmentType.getCanBeAltBlocking() != null && punishmentType.getCanBeAltBlocking()) {
            set(CirrusItem.of(
                    altBlocking ? ItemType.TORCH : ItemType.REDSTONE_TORCH,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Alt-Blocking: " + (altBlocking ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Toggle alt-blocking for this ban"
                    )
            ).slot(MenuSlots.SEVERITY_ALT_BLOCK).actionHandler("toggleAltBlock"));
        }

        // Slot 43: Stat-Wipe toggle (ban types only)
        if (isBanType && punishmentType.getCanBeStatWiping() != null && punishmentType.getCanBeStatWiping()) {
            set(CirrusItem.of(
                    statWipe ? ItemType.EXPERIENCE_BOTTLE : ItemType.GLASS_BOTTLE,
                    ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Stat-Wipe: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Toggle stat-wiping for this ban"
                    )
            ).slot(MenuSlots.SEVERITY_STAT_WIPE).actionHandler("toggleStatWipe"));
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Severity handlers
        registerActionHandler("issueLenient", (ActionHandler) click -> {
            issuePunishment(click, 0);
            return CallResult.DENY_GRABBING;
        });
        registerActionHandler("issueRegular", (ActionHandler) click -> {
            issuePunishment(click, 1);
            return CallResult.DENY_GRABBING;
        });
        registerActionHandler("issueAggravated", (ActionHandler) click -> {
            issuePunishment(click, 2);
            return CallResult.DENY_GRABBING;
        });

        // Toggle handlers
        registerActionHandler("toggleSilent", this::handleToggleSilent);
        registerActionHandler("toggleAltBlock", this::handleToggleAltBlock);
        registerActionHandler("toggleStatWipe", this::handleToggleStatWipe);

        // Override header navigation
        registerActionHandler("openNotes", (ActionHandler) click -> {
            click.clickedMenu().close();
            new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openAlts", (ActionHandler) click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openHistory", (ActionHandler) click -> {
            click.clickedMenu().close();
            new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openReports", (ActionHandler) click -> {
            click.clickedMenu().close();
            new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openPunish", (ActionHandler) click -> {
            click.clickedMenu().close();
            new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, parentBackAction)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });
    }

    private void issuePunishment(Click click, int severityLevel) {
        click.clickedMenu().close();

        // Prompt for reason
        ChatInputManager.requestInput(platform, viewerUuid, "Enter punishment reason for " + targetName + ":",
                reason -> {
                    // Create punishment request
                    String severityStr = severityLevel == 0 ? "lenient" : severityLevel == 1 ? "regular" : "aggravated";
                    PunishmentCreateRequest request = new PunishmentCreateRequest(
                            targetUuid.toString(),
                            viewerName,
                            punishmentType.getOrdinal(),
                            reason,
                            null, // duration - let API determine from severity
                            null, // data
                            null, // notes
                            null, // attachedTicketIds
                            severityStr,
                            silentMode ? "silent" : "active"
                    );

                    httpClient.createPunishmentWithResponse(request).thenAccept(response -> {
                        if (response.isSuccess()) {
                            sendMessage(MenuItems.COLOR_GREEN + "Punishment issued successfully!");

                            // Show post-punishment options
                            sendMessage("");
                            sendMessage(MenuItems.COLOR_GOLD + "Add additional information:");
                            sendMessage(MenuItems.COLOR_AQUA + "[Add Evidence by Link]" + MenuItems.COLOR_GRAY + " - Type 'evidence' followed by URL");
                            sendMessage(MenuItems.COLOR_AQUA + "[Add Note]" + MenuItems.COLOR_GRAY + " - Type 'note' followed by text");
                            sendMessage("");
                        } else {
                            sendMessage(MenuItems.COLOR_RED + "Failed to issue punishment: " + response.getMessage());
                        }
                    }).exceptionally(e -> {
                        sendMessage(MenuItems.COLOR_RED + "Failed to issue punishment: " + e.getMessage());
                        return null;
                    });
                },
                () -> {
                    sendMessage(MenuItems.COLOR_GRAY + "Punishment cancelled.");
                    platform.runOnMainThread(() -> display(click.player()));
                }
        );
    }

    private void handleToggleSilent(Click click) {
        silentMode = !silentMode;
        // Refresh menu
        click.clickedMenu().close();
        new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, parentBackAction)
                .display(click.player());
    }

    private void handleToggleAltBlock(Click click) {
        altBlocking = !altBlocking;
        // Refresh menu
        click.clickedMenu().close();
        new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, parentBackAction)
                .display(click.player());
    }

    private void handleToggleStatWipe(Click click) {
        statWipe = !statWipe;
        // Refresh menu
        click.clickedMenu().close();
        new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishmentType, parentBackAction)
                .display(click.player());
    }
}
