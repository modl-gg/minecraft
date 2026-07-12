package gg.modl.minecraft.core.util;

import java.util.Map;

public final class YamlValues {
    private YamlValues() {
    }

    public static String asString(Object value, String def) {
        return value instanceof String ? (String) value : def;
    }

    public static int asInt(Object value, int def) {
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    public static boolean asBoolean(Object value, boolean def) {
        return value instanceof Boolean ? (Boolean) value : def;
    }

    public static Object nested(Map<?, ?> map, String path) {
        Object current = map;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?>) current = ((Map<?, ?>) current).get(part);
            else return null;
        }
        return current;
    }

    public static String nestedString(Map<?, ?> map, String path, String def) {
        return asString(nested(map, path), def);
    }

    public static int nestedInt(Map<?, ?> map, String path, int def) {
        return asInt(nested(map, path), def);
    }

    public static boolean nestedBool(Map<?, ?> map, String path, boolean def) {
        return asBoolean(nested(map, path), def);
    }

    public static int toInt(Object value, int def) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static boolean toBoolean(Object value, boolean def) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }

    public static String coerceString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public static boolean coerceBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value == null) return fallback;
        String s = String.valueOf(value).trim();
        if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on")) return true;
        if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("off")) return false;
        return fallback;
    }
}
