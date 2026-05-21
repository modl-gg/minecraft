package gg.modl.minecraft.bridge.locale;

import gg.modl.minecraft.bridge.resource.BridgeYamlResource;
import gg.modl.minecraft.core.locale.LegacyTextRenderer;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class BridgeLocaleManager {
    private static final String LOCALE_RESOURCE = "/bridge_locale/en_US.yml";

    private final Logger logger;
    private Map<String, Object> messages = Collections.emptyMap();

    public BridgeLocaleManager(Logger logger) {
        this.logger = logger;
        load();
    }

    private void load() {
        try {
            Map<String, Object> data = BridgeYamlResource.loadResourceMap(getClass(), LOCALE_RESOURCE);
            if (!data.isEmpty()) {
                messages = data;
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale file: " + e.getMessage());
        }
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String raw = resolve(key);
        if (raw == null) return key;

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return colorize(raw);
    }

    public String getMessage(String key) {
        return getMessage(key, null);
    }

    public String colorize(String text) {
        return LegacyTextRenderer.colorize(text);
    }

    @SuppressWarnings("unchecked")
    private String resolve(String key) {
        String[] parts = key.split("\\.");
        Object current = messages;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current instanceof String ? (String) current : null;
    }
}
