package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Inspect Menu list screens (Notes, Alts, History, Reports).
 * Provides the common header with target player information and navigation items.
 *
 * @param <T> The type of elements displayed in the browser
 */
public abstract class BaseInspectListMenu<T> extends BaseListMenu<T> {

    protected final Account targetAccount;
    protected final String targetName;
    protected final UUID targetUuid;

    /**
     * Track which tab is currently active for enchantment glow.
     */
    public enum InspectTab {
        NONE, NOTES, ALTS, HISTORY, REPORTS, PUNISH
    }

    protected InspectTab activeTab = InspectTab.NONE;

    /**
     * Create a new inspect list menu.
     *
     * @param title The menu title
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to perform when back button is clicked
     */
    public BaseInspectListMenu(String title, Platform platform, ModlHttpClient httpClient,
                               UUID viewerUuid, String viewerName, Account targetAccount,
                               Consumer<CirrusPlayerWrapper> backAction) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add inspect menu header items in the second row
        // Slot 10: Player head with info
        items.put(MenuSlots.INSPECT_PLAYER_HEAD, createPlayerHeadItem());

        // Slot 12: Notes
        CirrusItem notesItem = CirrusItem.of(
                ItemType.PAPER,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Notes"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View and edit " + targetName + "'s staff notes",
                        MenuItems.COLOR_GRAY + "(" + targetAccount.getNotes().size() + " notes)"
                )
        ).actionHandler("openNotes");
        if (activeTab == InspectTab.NOTES) notesItem = addGlow(notesItem);
        items.put(MenuSlots.INSPECT_NOTES, notesItem);

        // Slot 13: Alts
        CirrusItem altsItem = CirrusItem.of(
                ItemType.VINE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Alts"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View " + targetName + "'s known alternate accounts"
                )
        ).actionHandler("openAlts");
        if (activeTab == InspectTab.ALTS) altsItem = addGlow(altsItem);
        items.put(MenuSlots.INSPECT_ALTS, altsItem);

        // Slot 14: History
        CirrusItem historyItem = CirrusItem.of(
                ItemType.WRITABLE_BOOK,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "History"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View " + targetName + "'s past punishments",
                        MenuItems.COLOR_GRAY + "(" + targetAccount.getPunishments().size() + " punishments)"
                )
        ).actionHandler("openHistory");
        if (activeTab == InspectTab.HISTORY) historyItem = addGlow(historyItem);
        items.put(MenuSlots.INSPECT_HISTORY, historyItem);

        // Slot 15: Reports
        CirrusItem reportsItem = CirrusItem.of(
                ItemType.ENDER_EYE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View and handle reports against " + targetName
                )
        ).actionHandler("openReports");
        if (activeTab == InspectTab.REPORTS) reportsItem = addGlow(reportsItem);
        items.put(MenuSlots.INSPECT_REPORTS, reportsItem);

        // Slot 17: Punish
        CirrusItem punishItem = CirrusItem.of(
                ItemType.BOW,
                ChatElement.ofLegacyText(MenuItems.COLOR_RED + "Punish"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue a new punishment against " + targetName
                )
        ).actionHandler("openPunish");
        if (activeTab == InspectTab.PUNISH) punishItem = addGlow(punishItem);
        items.put(MenuSlots.INSPECT_PUNISH, punishItem);

        return items;
    }

    /**
     * Create the player head item with account information.
     */
    protected CirrusItem createPlayerHeadItem() {
        List<String> lore = new ArrayList<>();

        // UUID
        lore.add(MenuItems.COLOR_GRAY + "UUID: " + MenuItems.COLOR_WHITE + targetUuid.toString());

        // Active punishments
        long activePunishments = targetAccount.getPunishments().stream()
                .filter(Punishment::isActive)
                .count();
        if (activePunishments > 0) {
            lore.add("");
            lore.add(MenuItems.COLOR_RED + "Active Punishments: " + activePunishments);
        }

        return CirrusItem.of(
                ItemType.PLAYER_HEAD,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + targetName + "'s Information"),
                MenuItems.lore(lore)
        );
    }

    /**
     * Add enchantment glow to an item.
     */
    protected CirrusItem addGlow(CirrusItem item) {
        // In Cirrus, we'd typically add an enchantment to create glow
        // For now, return as-is - implementation depends on Cirrus version
        return item;
    }

    /**
     * Get the current player name from the account.
     */
    protected String getPlayerName(Account account) {
        if (account.getUsernames() != null && !account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();

        // These will be overridden by subclasses to navigate to specific menus
        // Using Consumer<Click> explicitly for clarity
        java.util.function.Consumer<dev.simplix.cirrus.model.Click> noOp = click -> {};
        registerActionHandler("openNotes", noOp);
        registerActionHandler("openAlts", noOp);
        registerActionHandler("openHistory", noOp);
        registerActionHandler("openReports", noOp);
        registerActionHandler("openPunish", noOp);
    }

    /**
     * Get the target account.
     */
    public Account getTargetAccount() {
        return targetAccount;
    }

    /**
     * Get the target player's name.
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Get the target player's UUID.
     */
    public UUID getTargetUuid() {
        return targetUuid;
    }
}
