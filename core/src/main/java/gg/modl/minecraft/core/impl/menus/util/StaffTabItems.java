package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StaffTabItems {
    private StaffTabItems() {}

    public enum StaffTab {
        NONE, ONLINE_PLAYERS, REPORTS, PUNISHMENTS, TICKETS, SETTINGS
    }

    public static Map<Integer, CirrusItem> createItems() {
        return buildTabs(false);
    }

    public static Map<Integer, CirrusItem> createCompactItems() {
        return buildTabs(true);
    }

    private static Map<Integer, CirrusItem> buildTabs(boolean compact) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        put(items, compact ? MenuSlots.COMPACT_STAFF_ONLINE : MenuSlots.STAFF_ONLINE_PLAYERS,
                CirrusItemType.PLAYER_HEAD, MenuItems.COLOR_GOLD + "Online Players", "openOnlinePlayers",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "View players currently online")
                        : MenuItems.lore(
                                MenuItems.COLOR_GRAY + "View players currently online",
                                MenuItems.COLOR_GRAY + "Click to view online players"));

        put(items, compact ? MenuSlots.COMPACT_STAFF_REPORTS : MenuSlots.STAFF_REPORTS,
                CirrusItemType.ENDER_EYE, MenuItems.COLOR_GOLD + "Reports", "openReports",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "View unresolved reports")
                        : MenuItems.lore(
                                MenuItems.COLOR_GRAY + "View unresolved reports",
                                MenuItems.COLOR_GRAY + "Click to view reports"));

        put(items, compact ? MenuSlots.COMPACT_STAFF_PUNISHMENTS : MenuSlots.STAFF_PUNISHMENTS,
                CirrusItemType.IRON_SWORD, MenuItems.COLOR_GOLD + "Recent Punishments", "openPunishments",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "View recent punishments")
                        : MenuItems.lore(
                                MenuItems.COLOR_GRAY + "View recent punishments issued",
                                MenuItems.COLOR_GRAY + "across the server"));

        put(items, compact ? MenuSlots.COMPACT_STAFF_TICKETS : MenuSlots.STAFF_TICKETS,
                CirrusItemType.PAPER, MenuItems.COLOR_GOLD + "Support Tickets", "openTickets",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "View support tickets")
                        : MenuItems.lore(MenuItems.COLOR_GRAY + "View unresolved support tickets"));

        put(items, compact ? MenuSlots.COMPACT_STAFF_PANEL : MenuSlots.STAFF_PANEL_LINK,
                CirrusItemType.COMPASS, MenuItems.COLOR_AQUA + "Go to Panel", "openPanel",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Get the link to staff panel")
                        : MenuItems.lore(MenuItems.COLOR_GRAY + "Get the link to the staff panel"));

        put(items, compact ? MenuSlots.COMPACT_STAFF_SETTINGS : MenuSlots.STAFF_SETTINGS,
                CirrusItemType.COMMAND_BLOCK, MenuItems.COLOR_GOLD + "Settings", "openSettings",
                MenuItems.lore(MenuItems.COLOR_GRAY + "Modify staff settings"));

        return items;
    }

    private static void put(Map<Integer, CirrusItem> items, int slot, CirrusItemType type, String title,
                            String actionHandler, List<CirrusChatElement> lore) {
        items.put(slot, CirrusItem.of(type, CirrusChatElement.ofLegacyText(title), lore).actionHandler(actionHandler));
    }
}
