package gg.modl.minecraft.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Colors {
    public static Component get(String string) {
        // Convert § to & for legacy serializer
        String converted = string.replace('§', '&');
        return LegacyComponentSerializer.legacyAmpersand().deserialize(converted);
    }
}