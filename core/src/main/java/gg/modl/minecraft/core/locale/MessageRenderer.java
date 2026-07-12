package gg.modl.minecraft.core.locale;

public class MessageRenderer {

    public boolean isMiniMessage(String text) {
        return LegacyTextRenderer.isMiniMessage(text);
    }

    public String renderToLegacy(String rawText) {
        return LegacyTextRenderer.colorize(rawText);
    }

}
