package gg.modl.minecraft.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ConfiguredCommandAliases {
    private final Map<String, List<String>> aliasesByKey;

    public ConfiguredCommandAliases(Map<String, String> rawAliases) {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawAliases.entrySet()) {
            aliases.put(entry.getKey(), parseAliases(entry.getValue()));
        }
        this.aliasesByKey = Collections.unmodifiableMap(aliases);
    }

    public boolean isEnabled(String key) {
        return !aliasesFor(key).isEmpty();
    }

    public boolean anyEnabled(String... keys) {
        for (String key : keys) {
            if (isEnabled(key)) return true;
        }
        return false;
    }

    public List<String> aliasesFor(String key) {
        List<String> aliases = aliasesByKey.get(key);
        return aliases != null ? aliases : Collections.emptyList();
    }

    public String primaryAlias(String key) {
        List<String> aliases = aliasesFor(key);
        return aliases.isEmpty() ? null : aliases.get(0);
    }

    public String[] resolveCommandValues(String... declaredValues) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();

        for (String declaredValue : declaredValues) {
            if (declaredValue == null) continue;

            List<String> configuredAliases = aliasesByKey.get(declaredValue);
            if (configuredAliases != null) {
                resolved.addAll(configuredAliases);
                continue;
            }

            String trimmed = declaredValue.trim();
            if (!trimmed.isEmpty()) resolved.add(trimmed);
        }

        return resolved.toArray(new String[0]);
    }

    private static List<String> parseAliases(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String token : rawValue.split("\\|")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) aliases.add(trimmed);
        }

        return Collections.unmodifiableList(new ArrayList<>(aliases));
    }
}
