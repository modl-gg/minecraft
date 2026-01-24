package gg.modl.minecraft.core.impl.menus.base;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import dev.simplix.protocolize.api.chat.ChatElement;
import dev.simplix.protocolize.data.ItemType;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.MenuSlots;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Base class for Staff Menu list screens (Online Players, Reports, Punishments, Tickets).
 * Provides the common header with staff navigation items.
 *
 * @param <T> The type of elements displayed in the browser
 */
public abstract class BaseStaffListMenu<T> extends BaseListMenu<T> {

    /**
     * Track which tab is currently active for enchantment glow.
     */
    public enum StaffTab {
        NONE, ONLINE_PLAYERS, REPORTS, PUNISHMENTS, TICKETS, SETTINGS
    }

    protected StaffTab activeTab = StaffTab.NONE;
    protected final boolean isAdmin;

    /**
     * Create a new staff list menu.
     *
     * @param title The menu title
     * @param platform The platform instance
     * @param httpClient The HTTP client for API calls
     * @param viewerUuid The UUID of the staff viewing the menu
     * @param viewerName The name of the staff viewing the menu
     * @param isAdmin Whether the viewer has admin permissions
     * @param backAction Action to perform when back button is clicked
     */
    public BaseStaffListMenu(String title, Platform platform, ModlHttpClient httpClient,
                             UUID viewerUuid, String viewerName, boolean isAdmin,
                             Consumer<CirrusPlayerWrapper> backAction) {
        super(title, platform, httpClient, viewerUuid, viewerName, backAction);
        this.isAdmin = isAdmin;
    }

    @Override
    protected Map<Integer, CirrusItem> intercept(int menuSize) {
        Map<Integer, CirrusItem> items = super.intercept(menuSize);

        // Add staff menu header items in the second row
        // Slot 10: Online Players
        CirrusItem onlineItem = CirrusItem.of(
                ItemType.PLAYER_HEAD,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Online Players"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View players currently online",
                        MenuItems.COLOR_GRAY + "Click to view online players"
                )
        ).actionHandler("openOnlinePlayers");
        if (activeTab == StaffTab.ONLINE_PLAYERS) onlineItem = addGlow(onlineItem);
        items.put(MenuSlots.STAFF_ONLINE_PLAYERS, onlineItem);

        // Slot 11: Reports
        CirrusItem reportsItem = CirrusItem.of(
                ItemType.ENDER_EYE,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View unresolved reports",
                        MenuItems.COLOR_GRAY + "Click to view reports"
                )
        ).actionHandler("openReports");
        if (activeTab == StaffTab.REPORTS) reportsItem = addGlow(reportsItem);
        items.put(MenuSlots.STAFF_REPORTS, reportsItem);

        // Slot 12: Recent Punishments
        CirrusItem punishmentsItem = CirrusItem.of(
                ItemType.IRON_SWORD,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Recent Punishments"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View recent punishments issued",
                        MenuItems.COLOR_GRAY + "across the server"
                )
        ).actionHandler("openPunishments");
        if (activeTab == StaffTab.PUNISHMENTS) punishmentsItem = addGlow(punishmentsItem);
        items.put(MenuSlots.STAFF_PUNISHMENTS, punishmentsItem);

        // Slot 13: Support Tickets
        CirrusItem ticketsItem = CirrusItem.of(
                ItemType.PAPER,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Support Tickets"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View unresolved support tickets"
                )
        ).actionHandler("openTickets");
        if (activeTab == StaffTab.TICKETS) ticketsItem = addGlow(ticketsItem);
        items.put(MenuSlots.STAFF_TICKETS, ticketsItem);

        // Slot 15: Go to Panel
        items.put(MenuSlots.STAFF_PANEL_LINK, CirrusItem.of(
                ItemType.COMPASS,
                ChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Go to Panel"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Get the link to the staff panel"
                )
        ).actionHandler("openPanel"));

        // Slot 16: Settings
        CirrusItem settingsItem = CirrusItem.of(
                ItemType.COMMAND_BLOCK,
                ChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Settings"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Modify staff settings"
                )
        ).actionHandler("openSettings");
        if (activeTab == StaffTab.SETTINGS) settingsItem = addGlow(settingsItem);
        items.put(MenuSlots.STAFF_SETTINGS, settingsItem);

        return items;
    }

    /**
     * Add enchantment glow to an item.
     */
    protected CirrusItem addGlow(CirrusItem item) {
        // In Cirrus, we'd typically add an enchantment to create glow
        // For now, return as-is - implementation depends on Cirrus version
        return item;
    }

    @Override
    protected void registerActionHandlers() {
        super.registerActionHandlers();
        // Note: Tab navigation handlers (openOnlinePlayers, openReports, etc.)
        // MUST be registered by each subclass. Do not register no-op handlers here
        // as they would take precedence over the actual handlers in subclasses.
    }

    /**
     * Check if viewer has admin permissions.
     */
    public boolean isAdmin() {
        return isAdmin;
    }
}
