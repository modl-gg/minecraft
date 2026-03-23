package gg.modl.minecraft.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Colors {
    private static final char SECTION_SIGN = '§';

    public static Component get(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string.replace(SECTION_SIGN, '&'));
    }
}