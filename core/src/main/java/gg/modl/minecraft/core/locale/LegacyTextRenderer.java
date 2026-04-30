package gg.modl.minecraft.core.locale;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyTextRenderer {
    private static final char SECTION = '\u00a7';
    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[a-zA-Z_/!#][^>]*>");
    private static final Pattern MINIMESSAGE_TAG_REPLACE_PATTERN = Pattern.compile("<(/?)([^>\\s]+)(?:\\s[^>]*)?>");
    private static final Map<String, String> TAGS = createTags();

    private LegacyTextRenderer() {}

    public static boolean isMiniMessage(String text) {
        return text != null && !text.isEmpty() && MINIMESSAGE_TAG_PATTERN.matcher(text).find();
    }

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return text;
        return translateLegacyColorCodes(translateMiniMessageTags(text));
    }

    private static String translateMiniMessageTags(String text) {
        Matcher matcher = MINIMESSAGE_TAG_REPLACE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer(text.length());
        while (matcher.find()) {
            String replacement = resolveTag(matcher.group(1), matcher.group(2));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveTag(String closingMarker, String rawTag) {
        if (closingMarker != null && !closingMarker.isEmpty()) return "";

        String tag = rawTag.toLowerCase(Locale.ROOT);
        int argumentIndex = tag.indexOf(':');
        if (argumentIndex >= 0) {
            String name = tag.substring(0, argumentIndex);
            String argument = tag.substring(argumentIndex + 1);
            if ("color".equals(name) && isHexColor(argument)) return hexToLegacy(argument);
            return "";
        }

        if (isHexColor(tag)) return hexToLegacy(tag);
        String legacyCode = TAGS.get(tag);
        return legacyCode != null ? legacyCode : "";
    }

    private static boolean isHexColor(String tag) {
        return tag.length() == 7 && tag.charAt(0) == '#' && tag.substring(1).matches("[0-9a-f]{6}");
    }

    private static String hexToLegacy(String tag) {
        StringBuilder legacy = new StringBuilder(14);
        legacy.append(SECTION).append('x');
        for (int i = 1; i < tag.length(); i++) {
            legacy.append(SECTION).append(tag.charAt(i));
        }
        return legacy.toString();
    }

    private static String translateLegacyColorCodes(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static Map<String, String> createTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("black", code('0'));
        tags.put("dark_blue", code('1'));
        tags.put("dark_green", code('2'));
        tags.put("dark_aqua", code('3'));
        tags.put("dark_red", code('4'));
        tags.put("dark_purple", code('5'));
        tags.put("gold", code('6'));
        tags.put("gray", code('7'));
        tags.put("grey", code('7'));
        tags.put("dark_gray", code('8'));
        tags.put("dark_grey", code('8'));
        tags.put("blue", code('9'));
        tags.put("green", code('a'));
        tags.put("aqua", code('b'));
        tags.put("red", code('c'));
        tags.put("light_purple", code('d'));
        tags.put("yellow", code('e'));
        tags.put("white", code('f'));
        tags.put("obfuscated", code('k'));
        tags.put("bold", code('l'));
        tags.put("strikethrough", code('m'));
        tags.put("underlined", code('n'));
        tags.put("underline", code('n'));
        tags.put("italic", code('o'));
        tags.put("reset", code('r'));
        return Collections.unmodifiableMap(tags);
    }

    private static String code(char code) {
        return String.valueOf(SECTION) + code;
    }
}
