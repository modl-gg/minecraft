package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;

import java.util.HashMap;
import java.util.Map;

public final class InspectTabItems {
    private InspectTabItems() {}

    public enum InspectTab {
        NONE, NOTES, ALTS, HISTORY, REPORTS, PUNISH
    }

    public static Map<Integer, CirrusItem> createItems(InspectTab activeTab, Account targetAccount, String targetName) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        items.put(MenuSlots.INSPECT_NOTES, CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Notes"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View and edit " + targetName + "'s staff notes",
                        MenuItems.COLOR_GRAY + "(" + targetAccount.getNotes().size() + " notes)"
                )
        ).actionHandler("openNotes"));

        items.put(MenuSlots.INSPECT_ALTS, CirrusItem.of(
                CirrusItemType.VINE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Alts"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View " + targetName + "'s known alternate accounts"
                )
        ).actionHandler("openAlts"));

        items.put(MenuSlots.INSPECT_HISTORY, CirrusItem.of(
                CirrusItemType.WRITABLE_BOOK,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "History"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View " + targetName + "'s past punishments",
                        MenuItems.COLOR_GRAY + "(" + targetAccount.getPunishments().size() + " punishments)"
                )
        ).actionHandler("openHistory"));

        items.put(MenuSlots.INSPECT_REPORTS, CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "View and handle reports against " + targetName
                )
        ).actionHandler("openReports"));

        items.put(MenuSlots.INSPECT_PUNISH, CirrusItem.of(
                CirrusItemType.BOW,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "Punish"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue a new punishment against " + targetName
                )
        ).actionHandler("openPunish"));

        return items;
    }

    public static Map<Integer, CirrusItem> createCompactItems(InspectTab activeTab, Account targetAccount, String targetName) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        items.put(MenuSlots.COMPACT_INSPECT_NOTES, CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Notes"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Staff notes (" + targetAccount.getNotes().size() + ")"
                )
        ).actionHandler("openNotes"));

        items.put(MenuSlots.COMPACT_INSPECT_ALTS, CirrusItem.of(
                CirrusItemType.VINE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Alts"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Known alternate accounts"
                )
        ).actionHandler("openAlts"));

        items.put(MenuSlots.COMPACT_INSPECT_HISTORY, CirrusItem.of(
                CirrusItemType.WRITABLE_BOOK,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "History"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Punishments (" + targetAccount.getPunishments().size() + ")"
                )
        ).actionHandler("openHistory"));

        items.put(MenuSlots.COMPACT_INSPECT_REPORTS, CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Reports"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Reports against " + targetName
                )
        ).actionHandler("openReports"));

        items.put(MenuSlots.COMPACT_INSPECT_PUNISH, CirrusItem.of(
                CirrusItemType.BOW,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_RED + "Punish"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Issue a new punishment"
                )
        ).actionHandler("openPunish"));

        return items;
    }
}
