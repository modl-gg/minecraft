package gg.modl.minecraft.core.util;

public final class StringUtil {
    private StringUtil() {}
    public static String unescapeNewlines(String str) {
        if (str == null) return null;
        return str.replace("\\\\n", "\n").replace("\\n", "\n");
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
