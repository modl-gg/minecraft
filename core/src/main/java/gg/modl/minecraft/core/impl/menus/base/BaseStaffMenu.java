package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import dev.simplix.protocolize.data.inventory.InventoryType;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Staff Menu screens.
 * Provides the common header with staff navigation items.
 */
public abstract class BaseStaffMenu extends BaseMenu {

    /**
     * Track which tab is currently active for enchantment glow.
     */
    public enum StaffTab {
        NONE, ONLINE_PLAYERS, REPORTS, PUNISHMENTS, TICKETS, SETTINGS
    }

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;

    /**
     * Create a new staff menu with default 6-row size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param backAction Action to perform when back button is clicked (null if none)
     */
    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction) {
        super(platform, httpClient, viewerUuid, viewerName, backAction);
        this.isAdmin = isAdmin;
    }

    /**
     * Create a new staff menu with custom size.
     *
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param backAction Action to perform when back button is clicked (null if none)
     * @param inventoryType The inventory type/size for this menu
     */
    public BaseStaffMenu(Platform platform, ModlHttpClient httpClient, UUID viewerUuid, String viewerName,
                         boolean isAdmin, Consumer<CirrusPlayerWrapper> backAction, InventoryType inventoryType) {
        super(platform, httpClient, viewerUuid, viewerName, backAction, inventoryType);
        this.isAdmin = isAdmin;
    }

    /**
     * Build the standard staff menu header.
     * Call this in subclass constructors after setting activeTab.
     */
    protected void buildHeader() {
        fillBorders();

        // Slot 10: Online Players (player head)
        CirrusItem onlineItem = createItem(
                ItemType.PLAYER_HEAD,
                MenuItems.COLOR_GOLD + "Online Players",
                "openOnlinePlayers",
                MenuItems.COLOR_GRAY + "View players currently online",
                MenuItems.COLOR_GRAY + "Click to view online players"
        ).slot(MenuSlots.STAFF_ONLINE_PLAYERS);
        if (activeTab == StaffTab.ONLINE_PLAYERS) onlineItem = addGlow(onlineItem);
        set(onlineItem);

        // Slot 11: Reports (eye of ender)
        CirrusItem reportsItem = createItem(
                ItemType.ENDER_EYE,
                MenuItems.COLOR_GOLD + "Reports",
                "openReports",
                MenuItems.COLOR_GRAY + "View unresolved reports",
                MenuItems.COLOR_GRAY + "Click to view reports"
                // TODO: Add unresolved report count when endpoint available
        ).slot(MenuSlots.STAFF_REPORTS);
        if (activeTab == StaffTab.REPORTS) reportsItem = addGlow(reportsItem);
        set(reportsItem);

        // Slot 12: Recent Punishments (sword)
        CirrusItem punishmentsItem = createItem(
                ItemType.IRON_SWORD,
                MenuItems.COLOR_GOLD + "Recent Punishments",
                "openPunishments",
                MenuItems.COLOR_GRAY + "View recent punishments issued",
                MenuItems.COLOR_GRAY + "across the server"
        ).slot(MenuSlots.STAFF_PUNISHMENTS);
        if (activeTab == StaffTab.PUNISHMENTS) punishmentsItem = addGlow(punishmentsItem);
        set(punishmentsItem);

        // Slot 13: Support Tickets (paper)
        CirrusItem ticketsItem = createItem(
                ItemType.PAPER,
                MenuItems.COLOR_GOLD + "Support Tickets",
                "openTickets",
                MenuItems.COLOR_GRAY + "View unresolved support tickets"
                // TODO: Add unresolved ticket count when endpoint available
        ).slot(MenuSlots.STAFF_TICKETS);
        if (activeTab == StaffTab.TICKETS) ticketsItem = addGlow(ticketsItem);
        set(ticketsItem);

        // Slot 15: Go to Panel (compass)
        set(createItem(
                ItemType.COMPASS,
                MenuItems.COLOR_AQUA + "Go to Panel",
                "openPanel",
                MenuItems.COLOR_GRAY + "Get the link to the staff panel"
        ).slot(MenuSlots.STAFF_PANEL_LINK));

        // Slot 16: Settings (command block)
        CirrusItem settingsItem = createItem(
                ItemType.COMMAND_BLOCK,
                MenuItems.COLOR_GOLD + "Settings",
                "openSettings",
                MenuItems.COLOR_GRAY + "Modify staff settings"
        ).slot(MenuSlots.STAFF_SETTINGS);
        if (activeTab == StaffTab.SETTINGS) settingsItem = addGlow(settingsItem);
        set(settingsItem);

        // Add back button if needed
        addBackButton();
    }

    /**
     * Build a compact (3-row) staff menu header for the initial staff menu.
     */
    protected void buildCompactHeader() {
        // Slot 10: Online Players (player head)
        CirrusItem onlineItem = createItem(
                ItemType.PLAYER_HEAD,
                MenuItems.COLOR_GOLD + "Online Players",
                "openOnlinePlayers",
                MenuItems.COLOR_GRAY + "View players currently online"
        ).slot(MenuSlots.COMPACT_STAFF_ONLINE);
        if (activeTab == StaffTab.ONLINE_PLAYERS) onlineItem = addGlow(onlineItem);
        set(onlineItem);

        // Slot 11: Reports (eye of ender)
        CirrusItem reportsItem = createItem(
                ItemType.ENDER_EYE,
                MenuItems.COLOR_GOLD + "Reports",
                "openReports",
                MenuItems.COLOR_GRAY + "View unresolved reports"
        ).slot(MenuSlots.COMPACT_STAFF_REPORTS);
        if (activeTab == StaffTab.REPORTS) reportsItem = addGlow(reportsItem);
        set(reportsItem);

        // Slot 12: Recent Punishments (sword)
        CirrusItem punishmentsItem = createItem(
                ItemType.IRON_SWORD,
                MenuItems.COLOR_GOLD + "Recent Punishments",
                "openPunishments",
                MenuItems.COLOR_GRAY + "View recent punishments"
        ).slot(MenuSlots.COMPACT_STAFF_PUNISHMENTS);
        if (activeTab == StaffTab.PUNISHMENTS) punishmentsItem = addGlow(punishmentsItem);
        set(punishmentsItem);

        // Slot 13: Support Tickets (paper)
        CirrusItem ticketsItem = createItem(
                ItemType.PAPER,
                MenuItems.COLOR_GOLD + "Support Tickets",
                "openTickets",
                MenuItems.COLOR_GRAY + "View support tickets"
        ).slot(MenuSlots.COMPACT_STAFF_TICKETS);
        if (activeTab == StaffTab.TICKETS) ticketsItem = addGlow(ticketsItem);
        set(ticketsItem);

        // Slot 15: Go to Panel (compass)
        set(createItem(
                ItemType.COMPASS,
                MenuItems.COLOR_AQUA + "Go to Panel",
                "openPanel",
                MenuItems.COLOR_GRAY + "Get the link to staff panel"
        ).slot(MenuSlots.COMPACT_STAFF_PANEL));

        // Slot 16: Settings (command block)
        CirrusItem settingsItem = createItem(
                ItemType.COMMAND_BLOCK,
                MenuItems.COLOR_GOLD + "Settings",
                "openSettings",
                MenuItems.COLOR_GRAY + "Modify staff settings"
        ).slot(MenuSlots.COMPACT_STAFF_SETTINGS);
        if (activeTab == StaffTab.SETTINGS) settingsItem = addGlow(settingsItem);
        set(settingsItem);

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
     * Add enchantment glow to an item.
     */
    protected CirrusItem addGlow(CirrusItem item) {
        // In Cirrus, we'd typically add an enchantment to create glow
        // For now, return as-is - implementation depends on Cirrus version
        return item;
    }

    /**
     * Check if viewer has admin permissions.
     */
    public boolean isAdmin() {
        return isAdmin;
    }
}
