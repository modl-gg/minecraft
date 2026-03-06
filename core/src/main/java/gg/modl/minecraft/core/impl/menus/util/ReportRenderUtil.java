package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ReportRenderUtil {

    private ReportRenderUtil() {}

    public static CirrusItemType getReportItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        return switch (type.toLowerCase()) {
            case "gameplay" -> CirrusItemType.PLAYER_HEAD;
            case "chat" -> CirrusItemType.WRITABLE_BOOK;
            case "cheating" -> CirrusItemType.DIAMOND_SWORD;
            case "behavior" -> CirrusItemType.SKELETON_SKULL;
            default -> CirrusItemType.PAPER;
        };
    }

    public static List<String> processContent(String content) {
        if (content == null || content.isEmpty()) return List.of();

        content = StringUtil.unescapeNewlines(content);
        content = content.replace("**", "").replace("```", "");

        List<String> wrapped = new ArrayList<>();
        for (String paragraph : content.split("\n"))
            if (paragraph.trim().isEmpty())
                wrapped.add("");
            else
                wrapped.addAll(MenuItems.wrapText(paragraph.trim(), 7));
        return wrapped;
    }

    public static List<String> buildLore(LocaleManager locale, String localeKey, Map<String, String> vars) {
        List<String> lore = new ArrayList<>();
        for (String line : locale.getMessageList(localeKey)) {
            String processed = line;
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                processed = processed.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            if (processed.contains("\n"))
                lore.addAll(Arrays.asList(processed.split("\n")));
            else if (!processed.isEmpty())
                lore.add(processed);
        }
        return lore;
    }

    public static String getPlayerName(Account account) {
        account.getUsernames();
        if (!account.getUsernames().isEmpty()) {
            return account.getUsernames().stream()
                    .max((u1, u2) -> u1.getDate().compareTo(u2.getDate()))
                    .map(Account.Username::getUsername)
                    .orElse("Unknown");
        }
        return "Unknown";
    }
}
