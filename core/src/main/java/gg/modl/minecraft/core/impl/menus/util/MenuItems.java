package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.text.CirrusChatElement;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

public final class MenuItems {
    private MenuItems() {}

    private static volatile String dateFormatPattern = "MM/dd/yyyy HH:mm";
    private static volatile TimeZone timeZone = null;
    private static final ThreadLocal<SimpleDateFormat> FORMAT_CACHE = new ThreadLocal<>();

    public static void setDateFormat(String pattern) {
        try {
            new SimpleDateFormat(pattern);
            dateFormatPattern = pattern;
            FORMAT_CACHE.remove();
        } catch (IllegalArgumentException ignored) {}
    }

    public static void setTimezone(String timezoneId) {
        if (timezoneId != null && !timezoneId.isEmpty()) {
            timeZone = TimeZone.getTimeZone(timezoneId);
        }
        FORMAT_CACHE.remove();
    }

    public static final String COLOR_GOLD = "§6";
    public static final String COLOR_YELLOW = "§e";
    public static final String COLOR_GREEN = "§a";
    public static final String COLOR_RED = "§c";
    public static final String COLOR_GRAY = "§7";
    public static final String COLOR_DARK_GRAY = "§8";
    public static final String COLOR_WHITE = "§f";
    public static final String COLOR_AQUA = "§b";

    public static CirrusItem backButton() {
        return CirrusItem.of(
                CirrusItemType.RED_BED,
                CirrusChatElement.ofLegacyText(COLOR_RED + "Back"),
                lore(COLOR_GRAY + "Return to previous menu")
        ).actionHandler("back");
    }

    public static CirrusItem previousPageButton() {
        return CirrusItem.of(
                CirrusItemType.ARROW,
                CirrusChatElement.ofLegacyText(COLOR_YELLOW + "Previous Page")
        ).actionHandler("previousPage");
    }

    public static CirrusItem nextPageButton() {
        return CirrusItem.of(
                CirrusItemType.ARROW,
                CirrusChatElement.ofLegacyText(COLOR_YELLOW + "Next Page")
        ).actionHandler("nextPage");
    }

    public static CirrusItem playerHead(String playerName, String title, List<String> loreLines) {
        return CirrusItem.of(
                CirrusItemType.PLAYER_HEAD,
                CirrusChatElement.ofLegacyText(title),
                lore(loreLines)
        );
    }

    public static CirrusItem filterButton(String currentFilter, List<String> options) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "Filter by type:");
        loreLines.add("");
        for (String option : options)
            if (option.equalsIgnoreCase(currentFilter))
                loreLines.add(COLOR_GREEN + "▸ " + option);
            else
                loreLines.add(COLOR_GRAY + "  " + option);
        loreLines.add("");
        loreLines.add(COLOR_YELLOW + "Click to cycle filters");

        return CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Filter"),
                lore(loreLines)
        ).actionHandler("filter");
    }

    public static CirrusItem filterButton(String currentFilter, List<String> options, String currentStatusFilter, String itemLabel) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "Filter by type:");
        loreLines.add("");
        for (String option : options)
            if (option.equalsIgnoreCase(currentFilter))
                loreLines.add(COLOR_GREEN + "▸ " + option);
            else
                loreLines.add(COLOR_GRAY + "  " + option);
        loreLines.add("");
        loreLines.add(COLOR_YELLOW + "Click to cycle filters");
        String opposite = "open".equalsIgnoreCase(currentStatusFilter) ? "closed" : "open";
        loreLines.add(COLOR_YELLOW + "Right click to show only " + opposite + " " + itemLabel);

        return CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Filter"),
                lore(loreLines)
        ).actionHandler("filter");
    }

    public static CirrusItem sortButton(String currentSort, List<String> options) {
        List<String> loreLines = new ArrayList<>();
        loreLines.add(COLOR_GRAY + "Sort by:");
        loreLines.add("");
        for (String option : options)
            if (option.equalsIgnoreCase(currentSort))
                loreLines.add(COLOR_GREEN + "▸ " + option);
            else
                loreLines.add(COLOR_GRAY + "  " + option);
        loreLines.add("");
        loreLines.add(COLOR_YELLOW + "Click to cycle sort");

        return CirrusItem.of(
                CirrusItemType.ANVIL,
                CirrusChatElement.ofLegacyText(COLOR_GOLD + "Sort"),
                lore(loreLines)
        ).actionHandler("sort");
    }

    public static String formatDate(Date date) {
        if (date == null) return "Unknown";
        SimpleDateFormat sdf = FORMAT_CACHE.get();
        if (sdf == null) {
            sdf = new SimpleDateFormat(dateFormatPattern);
            if (timeZone != null) sdf.setTimeZone(timeZone);
            FORMAT_CACHE.set(sdf);
        }
        return sdf.format(date);
    }

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

    public static List<String> wrapText(String text, int wordsPerLine) {
        if (text == null || text.isEmpty())
            return new ArrayList<>();

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

        if (currentLine.length() > 0) lines.add(COLOR_WHITE + currentLine.toString().trim());

        return lines;
    }

    public static List<CirrusChatElement> lore(String... lines) {
        return Arrays.stream(lines)
                .map(MenuItems::translateColorCodes)
                .map(CirrusChatElement::ofLegacyText)
                .collect(Collectors.toList());
    }

    public static List<CirrusChatElement> lore(List<String> lines) {
        return lines.stream()
                .map(MenuItems::translateColorCodes)
                .map(CirrusChatElement::ofLegacyText)
                .collect(Collectors.toList());
    }

    public static String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }
}
