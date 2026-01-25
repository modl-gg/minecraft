package gg.modl.minecraft.core.impl.menus.inspect;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.base.BaseInspectListMenu;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * History Menu - displays punishment history for a player.
 */
public class HistoryMenu extends BaseInspectListMenu<Punishment> {

    private final Consumer<CirrusPlayerWrapper> backAction;
    private final Map<Integer, PunishmentTypesResponse.PunishmentTypeData> typesByOrdinal = new HashMap<>();

    /**
     * Create a new history menu.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to return to parent menu
     */
    public HistoryMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                       Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super("History: " + getPlayerNameStatic(targetAccount), platform, httpClient, viewerUuid, viewerName, targetAccount, backAction);
        this.backAction = backAction;
        activeTab = InspectTab.HISTORY;

        // Load punishment types for proper type name display
        loadPunishmentTypes();
    }

    private void loadPunishmentTypes() {
        try {
            httpClient.getPunishmentTypes().thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    for (PunishmentTypesResponse.PunishmentTypeData type : response.getData()) {
                        typesByOrdinal.put(type.getOrdinal(), type);
                    }
                }
            }).join();
        } catch (Exception e) {
            // Failed to load - will use fallback type detection
        }
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
    protected Collection<Punishment> elements() {
        // Punishments are stored in the account object
        List<Punishment> punishments = new ArrayList<>(targetAccount.getPunishments());

        // Return placeholder if empty to prevent Cirrus from shrinking inventory
        if (punishments.isEmpty()) {
            return Collections.singletonList(new Punishment());
        }

        // Sort by date, newest first
        punishments.sort((p1, p2) -> p2.getIssued().compareTo(p1.getIssued()));
        return punishments;
    }

    @Override
    protected CirrusItem map(Punishment punishment) {
        // Handle placeholder for empty list
        if (punishment.getId() == null || punishment.getId().isEmpty()) {
            return createEmptyPlaceholder("No punishment history");
        }

        List<String> lore = new ArrayList<>();

        // Type and status - get type name from loaded punishment types
        String typeName = getTypeName(punishment);
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        boolean isKick = typeData != null && typeData.isKick();
        boolean isActive = !isKick && punishment.isActive(); // Kicks are never "active"

        lore.add(MenuItems.COLOR_GRAY + "Type: " + (isActive ? MenuItems.COLOR_RED : MenuItems.COLOR_WHITE) + typeName);

        // Only show status for non-kicks (kicks are instant, not "active")
        if (!isKick) {
            lore.add(MenuItems.COLOR_GRAY + "Status: " + (isActive ? MenuItems.COLOR_RED + "Active" : MenuItems.COLOR_GREEN + "Expired/Pardoned"));
        }

        // Issuer
        lore.add(MenuItems.COLOR_GRAY + "Issued by: " + MenuItems.COLOR_WHITE + punishment.getIssuerName());

        // Date
        lore.add(MenuItems.COLOR_GRAY + "Date: " + MenuItems.COLOR_WHITE + MenuItems.formatDate(punishment.getIssued()));

        // Duration - don't show for kicks (they are instant)
        if (!isKick) {
            Long duration = punishment.getDuration();
            if (duration != null && duration > 0) {
                lore.add(MenuItems.COLOR_GRAY + "Duration: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(duration));
            } else {
                lore.add(MenuItems.COLOR_GRAY + "Duration: " + MenuItems.COLOR_RED + "Permanent");
            }
        }

        // Reason
        lore.add("");
        lore.add(MenuItems.COLOR_GRAY + "Reason:");
        lore.addAll(MenuItems.wrapText(punishment.getReason(), 6));

        // Action hint
        lore.add("");
        lore.add(MenuItems.COLOR_YELLOW + "Click to modify punishment");

        // Get appropriate item type based on punishment type
        ItemType itemType = getPunishmentItemType(punishment);

        // Kicks are never red since they're instant, not "active"
        String displayColor = isActive ? MenuItems.COLOR_RED : MenuItems.COLOR_GRAY;
        return CirrusItem.of(
                itemType,
                ChatElement.ofLegacyText(displayColor + typeName + " - " + MenuItems.formatDate(punishment.getIssued())),
                MenuItems.lore(lore)
        );
    }

    private String getTypeName(Punishment punishment) {
        // First try to get from our loaded punishment types
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getName() != null) {
            return typeData.getName();
        }

        // Fall back to dataMap typeName
        Object typeName = punishment.getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }

        // Last resort - use category detection
        return punishment.getTypeCategory();
    }

    private ItemType getPunishmentItemType(Punishment punishment) {
        // First check loaded punishment type category
        int ordinal = punishment.getTypeOrdinal();
        PunishmentTypesResponse.PunishmentTypeData typeData = typesByOrdinal.get(ordinal);
        if (typeData != null && typeData.getCategory() != null) {
            String category = typeData.getCategory().toLowerCase();
            if (category.contains("ban") || category.contains("security")) {
                return ItemType.BARRIER;
            } else if (category.contains("mute") || category.contains("social")) {
                return ItemType.PAPER;
            }
        }

        // Fall back to registry-based detection
        if (punishment.isBanType()) {
            return ItemType.BARRIER;
        } else if (punishment.isMuteType()) {
            return ItemType.PAPER;
        } else if (punishment.isKickType()) {
            return ItemType.LEATHER_BOOTS;
        }
        return ItemType.PAPER;
    }

    @Override
    protected void handleClick(Click click, Punishment punishment) {
        // Handle placeholder - do nothing
        if (punishment.getId() == null || punishment.getId().isEmpty()) {
            return;
        }

        // Open modify punishment menu - this is a secondary menu, back button returns to HistoryMenu
        // Pass backAction as rootBackAction so primary tab navigation preserves the back button
        ActionHandlers.openMenu(
                new ModifyPunishmentMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, punishment, backAction,
                        p -> new HistoryMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction).display(p)))
                .handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // Override header navigation handlers - pass backAction to preserve the back button
        registerActionHandler("openNotes", ActionHandlers.openMenu(
                new NotesMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openAlts", ActionHandlers.openMenu(
                new AltsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openHistory", click -> {
            // Already on history, do nothing
        });
        registerActionHandler("openReports", ActionHandlers.openMenu(
                new ReportsMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
        registerActionHandler("openPunish", ActionHandlers.openMenu(
                new PunishMenu(platform, httpClient, viewerUuid, viewerName, targetAccount, backAction)));
    }
}
