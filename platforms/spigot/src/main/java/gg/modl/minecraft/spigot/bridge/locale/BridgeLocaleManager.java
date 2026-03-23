package gg.modl.minecraft.spigot.bridge.locale;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.yaml.snakeyaml.Yaml;

import static gg.modl.minecraft.core.util.Java8Collections.*;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BridgeLocaleManager {
    private static final String LOCALE_RESOURCE = "/bridge_locale/en_US.yml";
    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[a-zA-Z_/!#][a-zA-Z0-9_:/.#-]*>");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TO_MINIMESSAGE = mapOfEntries(
            entry('0', "<black>"),         entry('1', "<dark_blue>"),
            entry('2', "<dark_green>"),     entry('3', "<dark_aqua>"),
            entry('4', "<dark_red>"),       entry('5', "<dark_purple>"),
            entry('6', "<gold>"),           entry('7', "<gray>"),
            entry('8', "<dark_gray>"),      entry('9', "<blue>"),
            entry('a', "<green>"),          entry('b', "<aqua>"),
            entry('c', "<red>"),            entry('d', "<light_purple>"),
            entry('e', "<yellow>"),         entry('f', "<white>"),
            entry('k', "<obfuscated>"),     entry('l', "<bold>"),
            entry('m', "<strikethrough>"),  entry('n', "<underlined>"),
            entry('o', "<italic>"),         entry('r', "<reset>")
    );

    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();
    private final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();
    private Map<String, Object> messages = Collections.emptyMap();

    public BridgeLocaleManager(Logger logger) {
        this.logger = logger;
        load();
    }

    private void load() {
        try (InputStream is = getClass().getResourceAsStream(LOCALE_RESOURCE)) {
            if (is == null) {
                logger.warning("Could not find " + LOCALE_RESOURCE + " in resources");
                return;
            }
            Map<String, Object> data = new Yaml().load(is);
            if (data != null) {
                messages = data;
            }
        } catch (Exception e) {
            logger.warning("Failed to load locale file: " + e.getMessage());
        }
    }

    private static boolean isMiniMessage(String text) {
        return text != null && !text.isEmpty() && MINIMESSAGE_TAG_PATTERN.matcher(text).find();
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
        if (text == null || text.isEmpty()) return text;
        if (isMiniMessage(text)) {
            return sectionSerializer.serialize(miniMessage.deserialize(legacyToMiniMessage(text)));
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String legacyToMiniMessage(String message) {
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer(message.length());
        while (matcher.find()) {
            String replacement = LEGACY_TO_MINIMESSAGE.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
