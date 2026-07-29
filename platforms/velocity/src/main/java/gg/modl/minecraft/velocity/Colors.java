package gg.modl.minecraft.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Colors {
    private static final char SECTION_SIGN = '§';

    private Colors() {}

    public static Component get(String string) {
        return legacy(string.replace(SECTION_SIGN, '&'));
    }

    public static Component legacy(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string);
    }
}
