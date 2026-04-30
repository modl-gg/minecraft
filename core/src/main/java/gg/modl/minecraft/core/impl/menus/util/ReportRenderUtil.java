package gg.modl.minecraft.core.impl.menus.util;

import dev.simplix.cirrus.item.CirrusItemType;
import gg.modl.minecraft.api.Account;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.util.StringUtil;

import java.util.*;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public final class ReportRenderUtil {

    private ReportRenderUtil() {}

    public static CirrusItemType getReportItemType(String type) {
        if (type == null) return CirrusItemType.PAPER;
        String lower = type.toLowerCase();
        if ("gameplay".equals(lower)) return CirrusItemType.DIAMOND_SWORD;
        if ("chat".equals(lower)) return CirrusItemType.WRITABLE_BOOK;
        return CirrusItemType.PAPER;
    }

    public static List<String> processContent(String content) {
        if (content == null || content.isEmpty()) return listOf();

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
        if (!account.getUsernames().isEmpty()) {
            Account.Username latestDated = null;
            String fallbackName = null;

            for (Account.Username username : account.getUsernames()) {
                if (username == null || username.getUsername() == null || username.getUsername().isEmpty()) {
                    continue;
                }

                fallbackName = username.getUsername();
                if (username.getDate() == null) {
                    continue;
                }

                if (latestDated == null || username.getDate().after(latestDated.getDate())) {
                    latestDated = username;
                }
            }

            if (latestDated != null) {
                return latestDated.getUsername();
            }
            if (fallbackName != null) {
                return fallbackName;
            }
        }
        return "Unknown";
    }
}
