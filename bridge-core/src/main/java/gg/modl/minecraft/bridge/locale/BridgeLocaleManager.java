package gg.modl.minecraft.bridge.locale;

import gg.modl.minecraft.bridge.resource.BridgeYamlResource;
import gg.modl.minecraft.core.locale.LegacyTextRenderer;
import gg.modl.minecraft.core.locale.YamlMessageResolver;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Collections;
import java.util.Map;

public class BridgeLocaleManager {
    private static final String LOCALE_RESOURCE = "/bridge_locale/en_US.yml";

    private final PluginLogger logger;
    private final YamlMessageResolver resolver = new YamlMessageResolver(LegacyTextRenderer::colorize);
    private Map<String, Object> messages = Collections.emptyMap();

    public BridgeLocaleManager(PluginLogger logger) {
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
        Object value = resolver.lookup(messages, key);
        if (!(value instanceof String)) return key;
        return resolver.render((String) value, placeholders);
    }

    public String getMessage(String key) {
        return getMessage(key, null);
    }

    public String colorize(String text) {
        return LegacyTextRenderer.colorize(text);
    }
}
