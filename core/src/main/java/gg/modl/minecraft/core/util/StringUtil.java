package gg.modl.minecraft.core.util;

public class StringUtil {
    public static String unescapeNewlines(String str) {
        if (str == null) return null;
        return str.replace("\\n", "\n").replace("\\\\n", "\n");
    }
}
