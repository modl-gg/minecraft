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
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Punish Menu - shows available punishment types.
 * Primary menu for issuing punishments.
 */
public class PunishMenu extends BaseInspectMenu {

    private List<PunishmentTypesResponse.PunishmentTypeData> punishmentTypes = new ArrayList<>();
    private final Consumer<CirrusPlayerWrapper> parentBackAction;

    /**
     * Create a new punish menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public PunishMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                      Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.parentBackAction = backAction;

        title("Punish: " + targetName);
        activeTab = InspectTab.PUNISH;

        // Fetch punishment types and build menu
        loadPunishmentTypes();
        buildHeader();
        buildPunishmentGrid();
    }

    private void loadPunishmentTypes() {
        // Try to fetch punishment types synchronously for initial display
        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    punishmentTypes = new ArrayList<>(response.getData());
                }
            }).join(); // Block to get data before building menu
        } catch (Exception e) {
            // API call failed - use empty list
        }
    }

    private void buildPunishmentGrid() {
        // Place punishment types in a grid (slots 28-34, 37-43)
        int[] gridSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        for (int i = 0; i < Math.min(punishmentTypes.size(), gridSlots.length); i++) {
            PunishmentTypesResponse.PunishmentTypeData type = punishmentTypes.get(i);
            set(createPunishmentTypeItem(type).slot(gridSlots[i]));
        }

        // If no punishment types loaded, show placeholder
        if (punishmentTypes.isEmpty()) {
            set(CirrusItem.of(
                    ItemType.BARRIER,
                    ChatElement.ofLegacyText(MenuItems.COLOR_RED + "No Punishment Types"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Failed to load punishment types",
                            MenuItems.COLOR_GRAY + "from the API"
                    )
            ).slot(31));
        }
    }

    private CirrusItem createPunishmentTypeItem(PunishmentTypesResponse.PunishmentTypeData type) {
        List<String> lore = new ArrayList<>();

        // Category
        if (type.getCategory() != null) {
            lore.add(MenuItems.COLOR_GRAY + "Category: " + MenuItems.COLOR_WHITE + type.getCategory());
        }

        // Description
        if (type.getStaffDescription() != null) {
            lore.add("");
            lore.addAll(MenuItems.wrapText(type.getStaffDescription(), 6));
        }

        // Show if it has severity levels
        Boolean singleSeverity = type.getSingleSeverityPunishment();
        if (singleSeverity == null || !singleSeverity) {
            lore.add("");
            lore.add(MenuItems.COLOR_YELLOW + "Click to select severity");
        } else {
            lore.add("");
            lore.add(MenuItems.COLOR_YELLOW + "Click to issue punishment");
        }

        // Determine item type based on category
        ItemType itemType = getItemTypeForCategory(type.getCategory());

        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(MenuItems.COLOR_RED + type.getName()),
                MenuItems.lore(lore)
        ).actionHandler("punishType_" + type.getId());
    }

    private ItemType getItemTypeForCategory(String category) {
        if (category == null) return ItemType.PAPER;

        switch (category.toLowerCase()) {
            case "ban":
            case "security":
                return ItemType.BARRIER;
            case "mute":
            case "chat":
                return ItemType.PAPER;
            case "kick":
                return ItemType.LEATHER_BOOTS;
            case "warning":
                return ItemType.YELLOW_WOOL;
            default:
                return ItemType.PAPER;
        }
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Register handler for each punishment type
        for (PunishmentTypesResponse.PunishmentTypeData type : punishmentTypes) {
            registerActionHandler("punishType_" + type.getId(), (ActionHandler) click -> {
                handlePunishmentType(click, type);
                return CallResult.DENY_GRABBING;
            });
        }

        // Override header navigation
        // Primary tabs should NOT have back button when switching between them - pass null
        registerActionHandler("openNotes", (ActionHandler) click -> {
            click.clickedMenu().close();
            new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openAlts", (ActionHandler) click -> {
            click.clickedMenu().close();
            new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openHistory", (ActionHandler) click -> {
            click.clickedMenu().close();
            new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openReports", (ActionHandler) click -> {
            click.clickedMenu().close();
            new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null)
                    .display(click.player());
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("openPunish", (ActionHandler) click -> {
            // Already on punish, do nothing
            return CallResult.DENY_GRABBING;
        });
    }

    private void handlePunishmentType(Click click, PunishmentTypesResponse.PunishmentTypeData type) {
        click.clickedMenu().close();

        // Check if this type has multiple severity levels
        Boolean singleSeverity = type.getSingleSeverityPunishment();
        if (singleSeverity != null && singleSeverity) {
            // Single severity - issue punishment directly
            // For now, open severity menu anyway for consistency
        }

        // Open severity selection menu - this is a secondary menu, back button returns to PunishMenu
        new PunishSeverityMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, type,
                player -> new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, null).display(player))
                .display(click.player());
    }
}
