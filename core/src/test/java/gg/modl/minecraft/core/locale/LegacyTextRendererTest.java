package gg.modl.minecraft.core.locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTextRendererTest {

    @Test
    void detectsMiniMessageTags() {
        assertTrue(LegacyTextRenderer.isMiniMessage("<red>Denied"));
        assertTrue(LegacyTextRenderer.isMiniMessage("<#55ff55>Allowed"));
        assertFalse(LegacyTextRenderer.isMiniMessage("&cDenied"));
    }

    @Test
    void colorizeTranslatesLegacyAmpersandCodes() {
        assertEquals("\u00a7cDenied \u00a7lnow", LegacyTextRenderer.colorize("&cDenied &lnow"));
    }

    @Test
    void colorizeTranslatesCommonMiniMessageTagsWithoutKyoriRuntime() {
        assertEquals("\u00a7cDenied \u00a7lnow", LegacyTextRenderer.colorize("<red>Denied <bold>now</bold></red>"));
    }

    @Test
    void colorizeKeepsTextReadableWhenTagsAreUnsupported() {
        assertEquals("Click here", LegacyTextRenderer.colorize("<click:run_command:'/help'>Click here</click>"));
    }
}
