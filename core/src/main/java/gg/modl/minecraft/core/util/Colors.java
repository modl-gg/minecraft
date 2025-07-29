package gg.modl.minecraft.core.util;

import dev.simplix.protocolize.api.chat.ChatElement;

import java.util.Date;
import java.util.List;

public class Colors {
    public static ChatElement<?> of(String string) {
        return ChatElement.ofLegacyText(translate(string));
    }

    public static String translate(String string) {
        return string.replace('&', '\u00a7');
    }

    public static String getHoverString(List<String> hover) {
        if (hover == null) return DateFormatter.format(new Date());

        String hoverString = String.join("\n", hover);
        return DateFormatter.format(new Date()) + (hoverString.isEmpty() ? "" : "\n" + hoverString);
    }
}
