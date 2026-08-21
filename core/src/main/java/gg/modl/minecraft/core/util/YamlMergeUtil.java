package gg.modl.minecraft.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlMergeUtil {
    private YamlMergeUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> userValues) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);

        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String key = entry.getKey();
            Object userValue = entry.getValue();
            Object defaultValue = merged.get(key);

            if (defaultValue instanceof Map && userValue instanceof Map) {
                merged.put(key, deepMerge((Map<String, Object>) defaultValue, (Map<String, Object>) userValue));
            } else {
                merged.put(key, userValue);
            }
        }

        return merged;
    }
}
