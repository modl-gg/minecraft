package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;

import java.util.HashMap;
import java.util.Map;

public final class StaffTabItems {
    private StaffTabItems() {}

    public enum StaffTab {
        NONE, ONLINE_PLAYERS, REPORTS, PUNISHMENTS, TICKETS, SETTINGS
    }

    public static Map<Integer, CirrusItem> createItems() {
        Map<Integer, CirrusItem> items = new HashMap<>();

        items.put(MenuSlots.STAFF_ONLINE_PLAYERS, CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Online Players"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View players currently online",
                        MenuItems.COLOR_GRAY + "Click to view online players"
                )
        ).actionHandler("openOnlinePlayers"));

        items.put(MenuSlots.STAFF_REPORTS, CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View unresolved reports",
                        MenuItems.COLOR_GRAY + "Click to view reports"
                )
        ).actionHandler("openReports"));

        items.put(MenuSlots.STAFF_PUNISHMENTS, CirrusItem.of(
                CirrusItemType.IRON_SWORD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Recent Punishments"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View recent punishments issued",
                        MenuItems.COLOR_GRAY + "across the server"
                )
        ).actionHandler("openPunishments"));

        items.put(MenuSlots.STAFF_TICKETS, CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Support Tickets"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View unresolved support tickets"
                )
        ).actionHandler("openTickets"));

        items.put(MenuSlots.STAFF_PANEL_LINK, CirrusItem.of(
                CirrusItemType.COMPASS,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Go to Panel"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Get the link to the staff panel"
                )
        ).actionHandler("openPanel"));

        items.put(MenuSlots.STAFF_SETTINGS, CirrusItem.of(
                CirrusItemType.COMMAND_BLOCK,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Settings"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Modify staff settings"
                )
        ).actionHandler("openSettings"));

        return items;
    }

    public static Map<Integer, CirrusItem> createCompactItems() {
        Map<Integer, CirrusItem> items = new HashMap<>();

        items.put(MenuSlots.COMPACT_STAFF_ONLINE, CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Online Players"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View players currently online"
                )
        ).actionHandler("openOnlinePlayers"));

        items.put(MenuSlots.COMPACT_STAFF_REPORTS, CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View unresolved reports"
                )
        ).actionHandler("openReports"));

        items.put(MenuSlots.COMPACT_STAFF_PUNISHMENTS, CirrusItem.of(
                CirrusItemType.IRON_SWORD,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Recent Punishments"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View recent punishments"
                )
        ).actionHandler("openPunishments"));

        items.put(MenuSlots.COMPACT_STAFF_TICKETS, CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Support Tickets"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View support tickets"
                )
        ).actionHandler("openTickets"));

        items.put(MenuSlots.COMPACT_STAFF_PANEL, CirrusItem.of(
                CirrusItemType.COMPASS,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Go to Panel"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Get the link to staff panel"
                )
        ).actionHandler("openPanel"));

        items.put(MenuSlots.COMPACT_STAFF_SETTINGS, CirrusItem.of(
                CirrusItemType.COMMAND_BLOCK,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Settings"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Modify staff settings"
                )
        ).actionHandler("openSettings"));

        return items;
    }
}
