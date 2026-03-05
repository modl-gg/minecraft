package gg.modl.minecraft.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Central text processing engine that auto-detects legacy (&amp;) or MiniMessage format
 * and produces Adventure Component objects.
 */
public class MessageRenderer {

    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[a-zA-Z_/!#][a-zA-Z0-9_:/.#-]*>");

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final LegacyComponentSerializer sectionSerializer;
    private final GsonComponentSerializer gsonSerializer;

    public MessageRenderer() {
        this.miniMessage = MiniMessage.builder().strict(false).build();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.sectionSerializer = LegacyComponentSerializer.legacySection();
        this.gsonSerializer = GsonComponentSerializer.gson();
    }

    /**
     * Check if text contains MiniMessage tags.
     */
    public static boolean isMiniMessage(String text) {
        if (text == null || text.isEmpty()) return false;
        return MINIMESSAGE_TAG_PATTERN.matcher(text).find();
    }

    /**
     * Render raw text to a Component, auto-detecting format.
     */
    public Component render(String rawText) {
        if (rawText == null || rawText.isEmpty()) return Component.empty();

        if (isMiniMessage(rawText)) {
            return miniMessage.deserialize(rawText);
        }
        return legacySerializer.deserialize(rawText);
    }

    /**
     * Render raw text with placeholders to a Component, auto-detecting format.
     * MiniMessage mode uses TagResolver for injection safety.
     * Legacy mode uses {key} string replacement.
     */
    public Component render(String rawText, Map<String, String> placeholders) {
        if (rawText == null || rawText.isEmpty()) return Component.empty();
        if (placeholders == null || placeholders.isEmpty()) return render(rawText);

        if (isMiniMessage(rawText)) {
            TagResolver.Builder resolverBuilder = TagResolver.builder();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                // Wrap values as plain text Components to prevent tag injection
                resolverBuilder.resolver(TagResolver.resolver(
                        entry.getKey(),
                        Tag.inserting(Component.text(entry.getValue()))
                ));
            }
            return miniMessage.deserialize(rawText, resolverBuilder.build());
        }

        // Legacy mode: simple string replacement then deserialize
        String message = rawText;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return legacySerializer.deserialize(message);
    }

    /**
     * Render a list of lines into a single Component joined by newlines.
     */
    public Component renderLines(List<String> lines, Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) return Component.empty();

        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(render(lines.get(i), placeholders));
        }
        return result;
    }

    /**
     * Flatten a Component to a legacy §-encoded string (for console, kick screens, etc).
     */
    public String componentToLegacy(Component component) {
        return sectionSerializer.serialize(component);
    }

    /**
     * Serialize a Component to JSON string (for sendJsonMessage).
     */
    public String componentToJson(Component component) {
        return gsonSerializer.serialize(component);
    }

    /**
     * Deserialize a legacy §-encoded string to a Component.
     */
    public Component fromLegacySection(String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) return Component.empty();
        return sectionSerializer.deserialize(legacyText);
    }
}
