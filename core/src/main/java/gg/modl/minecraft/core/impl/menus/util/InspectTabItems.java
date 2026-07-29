package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InspectTabItems {
    private InspectTabItems() {}

    public enum InspectTab {
        NONE, NOTES, ALTS, HISTORY, REPORTS, PUNISH
    }

    public static Map<Integer, CirrusItem> createItems(Account targetAccount, String targetName) {
        return createItems(targetAccount, targetName, targetAccount.getPunishments().size(), targetAccount.getNotes().size());
    }

    public static Map<Integer, CirrusItem> createItems(Account targetAccount, String targetName, int punishmentCount, int noteCount) {
        return buildTabs(false, targetName, punishmentCount, noteCount);
    }

    public static Map<Integer, CirrusItem> createCompactItems(Account targetAccount, String targetName) {
        return createCompactItems(targetAccount, targetName, targetAccount.getPunishments().size(), targetAccount.getNotes().size());
    }

    public static Map<Integer, CirrusItem> createCompactItems(Account targetAccount, String targetName, int punishmentCount, int noteCount) {
        return buildTabs(true, targetName, punishmentCount, noteCount);
    }

    private static Map<Integer, CirrusItem> buildTabs(boolean compact, String targetName, int punishmentCount, int noteCount) {
        Map<Integer, CirrusItem> items = new HashMap<>();

        put(items, compact ? MenuSlots.COMPACT_INSPECT_NOTES : MenuSlots.INSPECT_NOTES,
                CirrusItemType.PAPER, MenuItems.COLOR_GOLD + "Notes", "openNotes",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Staff notes (" + noteCount + ")")
                        : MenuItems.lore(
                                MenuItems.COLOR_GRAY + "View and edit " + targetName + "'s staff notes",
                                MenuItems.COLOR_GRAY + "(" + noteCount + " notes)"));

        put(items, compact ? MenuSlots.COMPACT_INSPECT_ALTS : MenuSlots.INSPECT_ALTS,
                CirrusItemType.VINE, MenuItems.COLOR_GOLD + "Alts", "openAlts",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Known alternate accounts")
                        : MenuItems.lore(MenuItems.COLOR_GRAY + "View " + targetName + "'s known alternate accounts"));

        put(items, compact ? MenuSlots.COMPACT_INSPECT_HISTORY : MenuSlots.INSPECT_HISTORY,
                CirrusItemType.WRITABLE_BOOK, MenuItems.COLOR_GOLD + "History", "openHistory",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Punishments (" + punishmentCount + ")")
                        : MenuItems.lore(
                                MenuItems.COLOR_GRAY + "View " + targetName + "'s past punishments",
                                MenuItems.COLOR_GRAY + "(" + punishmentCount + " punishments)"));

        put(items, compact ? MenuSlots.COMPACT_INSPECT_REPORTS : MenuSlots.INSPECT_REPORTS,
                CirrusItemType.ENDER_EYE, MenuItems.COLOR_GOLD + "Reports", "openReports",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Reports against " + targetName)
                        : MenuItems.lore(MenuItems.COLOR_GRAY + "View and handle reports against " + targetName));

        put(items, compact ? MenuSlots.COMPACT_INSPECT_PUNISH : MenuSlots.INSPECT_PUNISH,
                CirrusItemType.BOW, MenuItems.COLOR_RED + "Punish", "openPunish",
                compact
                        ? MenuItems.lore(MenuItems.COLOR_GRAY + "Issue a new punishment")
                        : MenuItems.lore(MenuItems.COLOR_GRAY + "Issue a new punishment against " + targetName));

        return items;
    }

    private static void put(Map<Integer, CirrusItem> items, int slot, CirrusItemType type, String title,
                            String actionHandler, List<CirrusChatElement> lore) {
        items.put(slot, CirrusItem.of(type, CirrusChatElement.ofLegacyText(title), lore).actionHandler(actionHandler));
    }
}
