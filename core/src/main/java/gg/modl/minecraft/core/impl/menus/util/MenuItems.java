package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Factory methods for creating common menu items.
 */
public final class MenuItems {
    private MenuItems() {}

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm");

    // Colors
    public static final String COLOR_GOLD = "§6";
    public static final String COLOR_YELLOW = "§e";
    public static final String COLOR_GREEN = "§a";
    public static final String COLOR_RED = "§c";
    public static final String COLOR_GRAY = "§7";
    public static final String COLOR_DARK_GRAY = "§8";
    public static final String COLOR_WHITE = "§f";
    public static final String COLOR_AQUA = "§b";

    /**
     * Create a glass pane filler item.
     */
    public static CirrusItem glassPaneFiller() {
        return CirrusItem.of(CirrusItemType.GRAY_STAINED_GLASS_PANE, CirrusChatElement.ofLegacyText(" "));
    }

    /**
     * Create a back button (red bed).
     */
    public static CirrusItem backButton() {
        return CirrusItem.of(
                CirrusItemType.of("minecraft:red_bed"),
                CirrusChatElement.ofLegacyText(COLOR_RED + "Back"),
                lore(COLOR_GRAY + "Return to previous menu")
        ).actionHandler("back");
    }

    /**
     * Create a previous page arrow.
     */
    public static CirrusItem previousPageButton() {
        return CirrusItem.of(
                CirrusItemType.ARROW,
                CirrusChatElement.ofLegacyText(COLOR_YELLOW + "Previous Page")
        ).actionHandler("previousPage");
    }

    /**
     * Create a next page arrow.
     */
    public static CirrusItem nextPageButton() {
        return CirrusItem.of(
                CirrusItemType.ARROW,
                CirrusChatElement.ofLegacyText(COLOR_YELLOW + "Next Page")
        ).actionHandler("nextPage");
    }

    /**
     * Create a page info item.
     */
    public static CirrusItem pageInfo(int current, int total) {
        return CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Page " + current + "/" + total)
        );
    }

    /**
     * Create a player head item.
     */
    public static CirrusItem playerHead(String playerName, UUID playerUuid, List<String> loreLines) {
        // Note: Skull texture setting would require additional NBT handling
        // For now, we create a basic player head
        return CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(COLOR_GOLD + playerName + "'s Information"),
                lore(loreLines)
        );
    }

    /**
     * Create a player head with custom title.
     */
    public static CirrusItem playerHead(String playerName, String title, List<String> loreLines) {
        return CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
                lore(loreLines)
        );
    }

    /**
     * Create a note item (paper).
     */
    public static CirrusItem noteItem(Date date, String staffName, String content) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "By: " + COLOR_WHITE + staffName);
        loreLines.add("");
        // Split content into lines (7 words per line)
        loreLines.addAll(wrapText(content, 7));

        return CirrusItem.of(
                CirrusItemType.PAPER,
                CirrusChatElement.ofLegacyText(COLOR_YELLOW + formatDate(date)),
                lore(loreLines)
        );
    }

    /**
     * Create a create note button.
     */
    public static CirrusItem createNoteButton(String playerName) {
        return CirrusItem.of(
                CirrusItemType.WRITABLE_BOOK,
                CirrusChatElement.ofLegacyText(COLOR_GREEN + "Create Note"),
                lore(COLOR_GRAY + "Add a new note for " + COLOR_WHITE + playerName)
        ).actionHandler("createNote");
    }

    /**
     * Create a filter button.
     */
    public static CirrusItem filterButton(String currentFilter, List<String> options) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "Filter by type:");
        loreLines.add("");
        for (String option : options) {
            if (option.equalsIgnoreCase(currentFilter)) {
                loreLines.add(COLOR_GREEN + "▸ " + option);
            } else {
                loreLines.add(COLOR_GRAY + "  " + option);
            }
        }
        loreLines.add("");
        loreLines.add(COLOR_YELLOW + "Click to cycle filters");

        return CirrusItem.of(
                CirrusItemType.of("minecraft:anvil"),
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Filter"),
                lore(loreLines)
        ).actionHandler("filter");
    }

    /**
     * Create a sort button.
     */
    public static CirrusItem sortButton(String currentSort, List<String> options) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "Sort by:");
        loreLines.add("");
        for (String option : options) {
            if (option.equalsIgnoreCase(currentSort)) {
                loreLines.add(COLOR_GREEN + "▸ " + option);
            } else {
                loreLines.add(COLOR_GRAY + "  " + option);
            }
        }
        loreLines.add("");
        loreLines.add(COLOR_YELLOW + "Click to cycle sort");

        return CirrusItem.of(
                CirrusItemType.of("minecraft:anvil"),
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Sort"),
                lore(loreLines)
        ).actionHandler("sort");
    }

    /**
     * Create punishment item based on type.
     */
    public static CirrusItemType getPunishmentItemType(String punishmentType) {
        if (punishmentType == null) return CirrusItemType.PAPER;

        return switch (punishmentType.toUpperCase()) {
            case "BAN", "SECURITY_BAN", "LINKED_BAN", "BLACKLIST" -> CirrusItemType.BARRIER;
            case "MUTE" -> CirrusItemType.PAPER;
            case "KICK" -> CirrusItemType.of("minecraft:leather_boots");
            default -> CirrusItemType.PAPER;
        };
    }

    /**
     * Create an enchanted version of an item (for selected tabs).
     */
    public static CirrusItem enchanted(CirrusItem item) {
        // Add enchantment glow
        // Note: This would require adding an enchantment to the item
        // For now, return the item as-is (implementation depends on Cirrus/Protocolize version)
        return item;
    }

    /**
     * Create a toggle item (lime/gray dye).
     */
    public static CirrusItem toggleItem(String title, String description, boolean enabled) {
        return CirrusItem.of(
                enabled ? CirrusItemType.of("minecraft:lime_dye") : CirrusItemType.of("minecraft:gray_dye"),
                CirrusChatElement.ofLegacyText((enabled ? COLOR_GREEN : COLOR_GRAY) + title + ": " + (enabled ? "Enabled" : "Disabled")),
                lore(COLOR_GRAY + description)
        );
    }

    /**
     * Format a date for display.
     */
    public static String formatDate(Date date) {
        if (date == null) return "Unknown";
        return DATE_FORMAT.format(date);
    }

    /**
     * Format a duration in milliseconds to human-readable string.
     */
    public static String formatDuration(Long durationMs) {
        if (durationMs == null || durationMs < 0) return "Permanent";

        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours % 24 > 0) sb.append(hours % 24).append("h ");
        if (minutes % 60 > 0) sb.append(minutes % 60).append("m ");
        if (seconds % 60 > 0 && days == 0) sb.append(seconds % 60).append("s");

        String result = sb.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }

    /**
     * Wrap text into lines of specified word count.
     */
    public static List<String> wrapText(String text, int wordsPerLine) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        String[] words = text.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            if (wordCount >= wordsPerLine) {
                lines.add(COLOR_WHITE + currentLine.toString().trim());
                currentLine = new StringBuilder();
                wordCount = 0;
            }
            currentLine.append(word).append(" ");
            wordCount++;
        }

        if (currentLine.length() > 0) {
            lines.add(COLOR_WHITE + currentLine.toString().trim());
        }

        return lines;
    }

    /**
     * Create lore from strings.
     */
    public static List<CirrusChatElement> lore(String... lines) {
        return Arrays.stream(lines)
                .map(MenuItems::translateColorCodes)
                .map(CirrusChatElement::ofLegacyText)
                .collect(Collectors.toList());
    }

    /**
     * Create lore from string list.
     */
    public static List<CirrusChatElement> lore(List<String> lines) {
        return lines.stream()
                .map(MenuItems::translateColorCodes)
                .map(CirrusChatElement::ofLegacyText)
                .collect(Collectors.toList());
    }

    /**
     * Translate & color codes to § color codes.
     */
    public static String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }
}
