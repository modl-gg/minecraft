package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;
import gg.modl.minecraft.core.locale.LocaleManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Inspect Menu screens.
 * Provides the common header with target player information and navigation items.
 */
public abstract class BaseInspectMenu extends BaseMenu {

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
     * Create a new inspect menu with default 6-row size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to perform when back button is clicked (null if none)
     */
    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    /**
     * Create a new inspect menu with custom size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param targetAccount The account being inspected
     * @param backAction Action to perform when back button is clicked (null if none)
     * @param inventoryType The inventory type/size for this menu
     */
    public BaseInspectMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                           Account targetAccount, Consumer<CirrusPlayerWrapper> backAction, CirrusInventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction, inventoryType);
        this.targetAccount = targetAccount;
        this.targetName = getPlayerName(targetAccount);
        this.targetUuid = targetAccount.getMinecraftUuid();
    }

    /**
     * Build the standard inspect menu header.
     * Call this in subclass constructors after setting activeTab.
     */
    protected void buildHeader() {
        fillBorders();

        // Slot 10: Player head with info
        set(createPlayerHeadItem().slot(MenuSlots.INSPECT_PLAYER_HEAD));

        // Slot 12: Notes
        CirrusItem notesItem = createItem(
                CirrusItemType.PAPER,
                MenuItems.COLOR_GOLD + "Notes",
                "openNotes",
                MenuItems.COLOR_GRAY + "View and edit " + targetName + "'s staff notes",
                MenuItems.COLOR_GRAY + "(" + targetAccount.getNotes().size() + " notes)"
        ).slot(MenuSlots.INSPECT_NOTES);
        if (activeTab == InspectTab.NOTES) notesItem = addGlow(notesItem);
        set(notesItem);

        // Slot 13: Alts (vine item)
        int altCount = 0; // Would need to fetch linked accounts
        CirrusItem altsItem = createItem(
                CirrusItemType.VINE,
                MenuItems.COLOR_GOLD + "Alts",
                "openAlts",
                MenuItems.COLOR_GRAY + "View " + targetName + "'s known alternate accounts",
                MenuItems.COLOR_GRAY + "(" + altCount + " alts)"
        ).slot(MenuSlots.INSPECT_ALTS);
        if (activeTab == InspectTab.ALTS) altsItem = addGlow(altsItem);
        set(altsItem);

        // Slot 14: History (book and quill)
        CirrusItem historyItem = createItem(
                CirrusItemType.WRITABLE_BOOK,
                MenuItems.COLOR_GOLD + "History",
                "openHistory",
                MenuItems.COLOR_GRAY + "View " + targetName + "'s past punishments",
                MenuItems.COLOR_GRAY + "(" + targetAccount.getPunishments().size() + " punishments)"
        ).slot(MenuSlots.INSPECT_HISTORY);
        if (activeTab == InspectTab.HISTORY) historyItem = addGlow(historyItem);
        set(historyItem);

        // Slot 15: Reports (eye of ender)
        CirrusItem reportsItem = createItem(
                CirrusItemType.ENDER_EYE,
                MenuItems.COLOR_GOLD + "Reports",
                "openReports",
                MenuItems.COLOR_GRAY + "View and handle reports against " + targetName
                // TODO: Add report count when endpoint available
        ).slot(MenuSlots.INSPECT_REPORTS);
        if (activeTab == InspectTab.REPORTS) reportsItem = addGlow(reportsItem);
        set(reportsItem);

        // Slot 17: Punish (mace for 1.21+, bow for older)
        // Using bow as a safe fallback
        CirrusItem punishItem = createItem(
                CirrusItemType.BOW,
                MenuItems.COLOR_RED + "Punish",
                "openPunish",
                MenuItems.COLOR_GRAY + "Issue a new punishment against " + targetName
        ).slot(MenuSlots.INSPECT_PUNISH);
        if (activeTab == InspectTab.PUNISH) punishItem = addGlow(punishItem);
        set(punishItem);

        // Add back button if needed
        addBackButton();
    }

    /**
     * Build a compact (3-row) inspect menu header for the initial inspect menu.
     */
    protected void buildCompactHeader() {
        // Slot 10: Player head with info
        set(createPlayerHeadItem().slot(MenuSlots.COMPACT_INSPECT_HEAD));

        // Slot 11: Notes
        CirrusItem notesItem = createItem(
                CirrusItemType.PAPER,
                MenuItems.COLOR_GOLD + "Notes",
                "openNotes",
                MenuItems.COLOR_GRAY + "Staff notes (" + targetAccount.getNotes().size() + ")"
        ).slot(MenuSlots.COMPACT_INSPECT_NOTES);
        if (activeTab == InspectTab.NOTES) notesItem = addGlow(notesItem);
        set(notesItem);

        // Slot 12: Alts
        CirrusItem altsItem = createItem(
                CirrusItemType.VINE,
                MenuItems.COLOR_GOLD + "Alts",
                "openAlts",
                MenuItems.COLOR_GRAY + "Known alternate accounts"
        ).slot(MenuSlots.COMPACT_INSPECT_ALTS);
        if (activeTab == InspectTab.ALTS) altsItem = addGlow(altsItem);
        set(altsItem);

        // Slot 13: History
        CirrusItem historyItem = createItem(
                CirrusItemType.WRITABLE_BOOK,
                MenuItems.COLOR_GOLD + "History",
                "openHistory",
                MenuItems.COLOR_GRAY + "Punishments (" + targetAccount.getPunishments().size() + ")"
        ).slot(MenuSlots.COMPACT_INSPECT_HISTORY);
        if (activeTab == InspectTab.HISTORY) historyItem = addGlow(historyItem);
        set(historyItem);

        // Slot 14: Reports
        CirrusItem reportsItem = createItem(
                CirrusItemType.ENDER_EYE,
                MenuItems.COLOR_GOLD + "Reports",
                "openReports",
                MenuItems.COLOR_GRAY + "Reports against " + targetName
        ).slot(MenuSlots.COMPACT_INSPECT_REPORTS);
        if (activeTab == InspectTab.REPORTS) reportsItem = addGlow(reportsItem);
        set(reportsItem);

        // Slot 16: Punish
        CirrusItem punishItem = createItem(
                CirrusItemType.BOW,
                MenuItems.COLOR_RED + "Punish",
                "openPunish",
                MenuItems.COLOR_GRAY + "Issue a new punishment"
        ).slot(MenuSlots.COMPACT_INSPECT_PUNISH);
        if (activeTab == InspectTab.PUNISH) punishItem = addGlow(punishItem);
        set(punishItem);

        // Add back button in compact position if needed
        addCompactBackButton();
    }

    /**
     * Add back button for compact (3-row) menus.
     */
    protected void addCompactBackButton() {
        if (backAction != null) {
            set(MenuItems.backButton().slot(MenuSlots.COMPACT_BACK_BUTTON));
        }
    }

    /**
     * Create the player head item with account information.
     */
    protected CirrusItem createPlayerHeadItem() {
        LocaleManager locale = platform.getLocaleManager();
        List<String> lore = new ArrayList<>();

        // Calculate first login from earliest username date
        String firstLogin = "Unknown";
        if (!targetAccount.getUsernames().isEmpty()) {
            Date earliest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .min(Date::compareTo)
                    .orElse(null);
            if (earliest != null) {
                firstLogin = MenuItems.formatDate(earliest);
            }
        }

        // Check online status and ban/mute status from cache
        boolean isOnline = platform.getCache() != null && platform.getCache().isOnline(targetUuid);
        boolean isBanned = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isBanType());
        boolean isMuted = targetAccount.getPunishments().stream()
                .anyMatch(p -> p.isActive() && p.isMuteType());

        // Real IP logged
        boolean realIpLogged = !targetAccount.getIpList().isEmpty();

        // Session time (if online) or last seen (if offline)
        String lastSeenOrSessionTime = "N/A";
        if (isOnline && platform.getCache() != null) {
            // Player is online - show session time
            long sessionMs = platform.getCache().getSessionDuration(targetUuid);
            lastSeenOrSessionTime = MenuItems.formatDuration(sessionMs);
        } else if (!targetAccount.getUsernames().isEmpty()) {
            // Player is offline - show last seen date
            Date latest = targetAccount.getUsernames().stream()
                    .map(Account.Username::getDate)
                    .filter(d -> d != null)
                    .max(Date::compareTo)
                    .orElse(null);
            if (latest != null) {
                lastSeenOrSessionTime = MenuItems.formatDate(latest);
            }
        }

        // Build variables map using HashMap to allow more entries
        Map<String, String> vars = new HashMap<>();
        vars.put("player_name", targetName);
        vars.put("uuid", targetUuid.toString());
        vars.put("first_login", firstLogin);
        vars.put("is_online", isOnline ? "&aYes" : "&cNo");
        vars.put("last_seen_or_session_time", lastSeenOrSessionTime);
        vars.put("playtime", "N/A");
        vars.put("real_ip_logged", realIpLogged ? "&aYes" : "&cNo");
        vars.put("is_banned", isBanned ? "&cYes" : "&aNo");
        vars.put("is_muted", isMuted ? "&cYes" : "&aNo");

        // Get lore lines from locale and substitute variables
        List<String> loreLinesRaw = locale.getMessageList("menus.player_head.lore");
        for (String line : loreLinesRaw) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            lore.add(processed);
        }

        // Get title from locale and translate color codes
        String title = locale.getMessage("menus.player_head.title", Map.of("player_name", targetName));
        title = MenuItems.translateColorCodes(title);

        return CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
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
            // Get the most recent username
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
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
