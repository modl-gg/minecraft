package gg.modl.minecraft.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

public class MessageRenderer {

    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[a-zA-Z_/!#][a-zA-Z0-9_:/.#-]*>");

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final LegacyComponentSerializer sectionSerializer;

    public MessageRenderer() {
        this.miniMessage = MiniMessage.builder().strict(false).build();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.sectionSerializer = LegacyComponentSerializer.legacySection();
    }

    public static boolean isMiniMessage(String text) {
        if (text == null || text.isEmpty()) return false;
        return MINIMESSAGE_TAG_PATTERN.matcher(text).find();
    }

    public Component render(String rawText) {
        if (rawText == null || rawText.isEmpty()) return Component.empty();

        if (isMiniMessage(rawText)) return miniMessage.deserialize(rawText);
        return legacySerializer.deserialize(rawText);
    }

    public String componentToLegacy(Component component) {
        return sectionSerializer.serialize(component);
    }

}
