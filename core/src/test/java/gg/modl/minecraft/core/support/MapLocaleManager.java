package gg.modl.minecraft.core.support;

import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class MapLocaleManager extends LocaleManager {
    private final Map<String, String> messages = new HashMap<>();
    private Function<String, String> fallback = path -> null;

    public MapLocaleManager put(String path, String message) {
        messages.put(path, message);
        return this;
    }

    public MapLocaleManager withFallback(Function<String, String> fallback) {
        this.fallback = fallback;
        return this;
    }

    private String resolve(String path) {
        return messages.containsKey(path) ? messages.get(path) : fallback.apply(path);
    }

    @Override
    public String getMessage(String path) {
        return resolve(path);
    }

    @Override
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = resolve(path);
        if (message == null) return null;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
}
