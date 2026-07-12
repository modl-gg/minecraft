package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Punishment;

import java.util.ArrayList;
import java.util.List;

public final class PunishmentModifyItemFactory {

    private final Punishment punishment;

    public PunishmentModifyItemFactory(Punishment punishment) {
        this.punishment = punishment;
    }

    public boolean isBanType() {
        return punishment.getType() == Punishment.Type.BAN ||
                punishment.getType() == Punishment.Type.SECURITY_BAN ||
                punishment.getType() == Punishment.Type.LINKED_BAN ||
                punishment.getType() == Punishment.Type.BLACKLIST;
    }

    public CirrusItem addNote(boolean canModify) {
        if (canModify) {
            return CirrusItem.of(
                    CirrusItemType.OAK_SIGN,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_YELLOW + "Add Note"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Add a staff note to this punishment"
                    )
            ).slot(MenuSlots.MODIFY_ADD_NOTE).actionHandler("addNote");
        }
        return CirrusItem.of(
                CirrusItemType.OAK_SIGN,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Add Note"),
                MenuItems.lore(
                        MenuItems.COLOR_RED + "No Permission"
                )
        ).slot(MenuSlots.MODIFY_ADD_NOTE);
    }

    public CirrusItem evidence(boolean canModify) {
        List<String> evidenceLore = new ArrayList<>();
        List<Evidence> evidenceList = punishment.getEvidence();
        if (evidenceList.isEmpty()) {
            evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "(No evidence attached)");
        } else {
            evidenceLore.add(MenuItems.COLOR_GRAY + "Current evidence (" + evidenceList.size() + "):");
            for (int i = 0; i < Math.min(evidenceList.size(), 5); i++) {
                Evidence ev = evidenceList.get(i);
                String display = ev.getDisplayText();
                if (display.length() > 40) display = display.substring(0, 37) + "...";
                evidenceLore.add(MenuItems.COLOR_WHITE + "- " + display);
            }
            if (evidenceList.size() > 5)
                evidenceLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (evidenceList.size() - 5) + " more");
        }

        CirrusItemType itemType = evidenceList.isEmpty() ? CirrusItemType.ARROW : CirrusItemType.SPECTRAL_ARROW;

        if (canModify) {
            evidenceLore.add("");
            evidenceLore.add(MenuItems.COLOR_YELLOW + "Left-click to add evidence");
            if (!evidenceList.isEmpty())
                evidenceLore.add(MenuItems.COLOR_YELLOW + "Right-click to view in chat");

            return CirrusItem.of(
                    itemType,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_AQUA + "Evidence"),
                    MenuItems.lore(evidenceLore)
            ).slot(MenuSlots.MODIFY_EVIDENCE).actionHandler("evidence");
        }

        evidenceLore.add("");
        evidenceLore.add(MenuItems.COLOR_RED + "No Permission to modify");

        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Evidence"),
                MenuItems.lore(evidenceLore)
        ).slot(MenuSlots.MODIFY_EVIDENCE);
    }

    public CirrusItem pardon(boolean canModify) {
        if (canModify) {
            return CirrusItem.of(
                    CirrusItemType.GOLDEN_APPLE,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GREEN + "Pardon Punishment"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Remove punishment and clear",
                            MenuItems.COLOR_GRAY + "associated points"
                    )
            ).slot(MenuSlots.MODIFY_PARDON).actionHandler("pardon");
        }
        return CirrusItem.of(
                CirrusItemType.GOLDEN_APPLE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Pardon Punishment"),
                MenuItems.lore(
                        MenuItems.COLOR_RED + "No Permission"
                )
        ).slot(MenuSlots.MODIFY_PARDON);
    }

    public CirrusItem duration(boolean canModify) {
        Long effectiveDuration = punishment.getEffectiveDuration();
        if (canModify) {
            return CirrusItem.of(
                    CirrusItemType.ANVIL,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Change Duration"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + "Shorten or lengthen punishment duration",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(effectiveDuration)
                    )
            ).slot(MenuSlots.MODIFY_DURATION).actionHandler("changeDuration");
        }
        return CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Change Duration"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Current: " + MenuItems.COLOR_WHITE + MenuItems.formatDuration(effectiveDuration),
                        "",
                        MenuItems.COLOR_RED + "No Permission"
                )
        ).slot(MenuSlots.MODIFY_DURATION);
    }

    public CirrusItem linkedTickets(boolean canModify) {
        List<String> ticketIds = punishment.getAttachedTicketIds();
        List<String> ticketLore = new ArrayList<>();
        if (ticketIds.isEmpty()) {
            ticketLore.add(MenuItems.COLOR_DARK_GRAY + "(No linked tickets)");
        } else {
            ticketLore.add(MenuItems.COLOR_GRAY + "Linked tickets (" + ticketIds.size() + "):");
            for (int i = 0; i < Math.min(ticketIds.size(), 5); i++) {
                ticketLore.add(MenuItems.COLOR_WHITE + "- " + ticketIds.get(i));
            }
            if (ticketIds.size() > 5)
                ticketLore.add(MenuItems.COLOR_DARK_GRAY + "... and " + (ticketIds.size() - 5) + " more");
        }
        ticketLore.add("");
        ticketLore.add(MenuItems.COLOR_YELLOW + "Left-click to view tickets");
        if (canModify)
            ticketLore.add(MenuItems.COLOR_YELLOW + "Right-click to link reports");

        return CirrusItem.of(
                CirrusItemType.ENDER_EYE,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Linked Tickets"),
                MenuItems.lore(ticketLore)
        ).slot(MenuSlots.MODIFY_LINKED_TICKETS).actionHandler("linkedTickets");
    }

    public CirrusItem statWipe(boolean canModify) {
        boolean statWipe = Boolean.TRUE.equals(punishment.getDataMap().get("wipeAfterExpiry"));
        CirrusItemType itemType = statWipe ? CirrusItemType.EXPERIENCE_BOTTLE : CirrusItemType.GLASS_BOTTLE;
        if (canModify) {
            return CirrusItem.of(
                    itemType,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Stat-Wipe"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (statWipe ? "Disable" : "Enable") + " stat-wiping for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_STAT_WIPE).actionHandler("toggleStatWipe");
        }
        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Toggle Stat-Wipe"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Current: " + (statWipe ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled"),
                        "",
                        MenuItems.COLOR_RED + "No Permission"
                )
        ).slot(MenuSlots.MODIFY_STAT_WIPE);
    }

    public CirrusItem altBlock(boolean canModify) {
        boolean altBlock = Boolean.TRUE.equals(punishment.getDataMap().get("altBlocking"));
        CirrusItemType itemType = altBlock ? CirrusItemType.TORCH : CirrusItemType.REDSTONE_TORCH;
        if (canModify) {
            return CirrusItem.of(
                    itemType,
                    CirrusChatElement.ofLegacyText(MenuItems.COLOR_GOLD + "Toggle Alt-Blocking"),
                    MenuItems.lore(
                            MenuItems.COLOR_GRAY + (altBlock ? "Disable" : "Enable") + " alt-blocking for this ban",
                            "",
                            MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled")
                    )
            ).slot(MenuSlots.MODIFY_ALT_BLOCK).actionHandler("toggleAltBlock");
        }
        return CirrusItem.of(
                itemType,
                CirrusChatElement.ofLegacyText(MenuItems.COLOR_GRAY + "Toggle Alt-Blocking"),
                MenuItems.lore(
                        MenuItems.COLOR_GRAY + "Current: " + (altBlock ? MenuItems.COLOR_GREEN + "Enabled" : MenuItems.COLOR_RED + "Disabled"),
                        "",
                        MenuItems.COLOR_RED + "No Permission"
                )
        ).slot(MenuSlots.MODIFY_ALT_BLOCK);
    }
}
