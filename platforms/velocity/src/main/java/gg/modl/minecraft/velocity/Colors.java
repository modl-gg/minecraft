package gg.modl.minecraft.velocity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Colors {

    private static final char SECTION_SIGN = '\u00A7';

    /** Converts a legacy color-coded string (using & or section sign) to an Adventure Component. */
    public static Component get(String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string.replace(SECTION_SIGN, '&'));
    }
}