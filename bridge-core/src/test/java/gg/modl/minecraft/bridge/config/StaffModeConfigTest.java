package gg.modl.minecraft.bridge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffModeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsConfiguredHotbarAndScoreboardValues() throws IOException {
        writeStaffModeConfig(
                "vanish_on_enable: false",
                "staff_hotbar:",
                "  2:",
                "    item: \"minecraft:stick\"",
                "    name: \"&aInspect\"",
                "    action: \"target_selector\"",
                "    lore:",
                "      - \"&7Line one\"",
                "target_hotbar:",
                "  5:",
                "    item: \"minecraft:book\"",
                "    name: \"&eNotes\"",
                "    action: \"inspect_target\"",
                "staff_scoreboard:",
                "  enabled: true",
                "  title: \"&bStaff\"",
                "  vanish: \" &7[V]\"",
                "  lines:",
                "    - \"&fPlayers: &a{online}\""
        );

        StaffModeConfig config = StaffModeConfig.load(tempDir, Logger.getLogger("test"));

        assertFalse(config.isVanishOnEnable());
        assertEquals("minecraft:stick", config.getStaffHotbar().get(2).getItem());
        assertEquals("&aInspect", config.getStaffHotbar().get(2).getName());
        assertEquals("target_selector", config.getStaffHotbar().get(2).getAction());
        assertEquals("&7Line one", config.getStaffHotbar().get(2).getLore().get(0));
        assertEquals("minecraft:book", config.getTargetHotbar().get(5).getItem());
        assertTrue(config.getStaffScoreboard().isEnabled());
        assertEquals("&bStaff", config.getStaffScoreboard().getTitle());
    }

    @Test
    void usesDefaultsWhenFileIsMissing() {
        StaffModeConfig config = StaffModeConfig.load(tempDir, Logger.getLogger("test"));

        assertTrue(config.isVanishOnEnable());
        assertEquals("minecraft:lead", config.getStaffHotbar().get(0).getItem());
        assertEquals("target_selector", config.getStaffHotbar().get(0).getAction());
        assertEquals("minecraft:ice", config.getTargetHotbar().get(0).getItem());
        assertEquals("freeze_target", config.getTargetHotbar().get(0).getAction());
    }

    @Test
    void usesDefaultsWhenFileIsMalformed() throws IOException {
        writeStaffModeConfig(
                "staff_hotbar:",
                "  0:",
                "    item: ["
        );

        StaffModeConfig config = StaffModeConfig.load(tempDir, Logger.getLogger("test"));

        assertTrue(config.isVanishOnEnable());
        assertEquals("minecraft:lead", config.getStaffHotbar().get(0).getItem());
        assertEquals("minecraft:ice", config.getTargetHotbar().get(0).getItem());
    }

    private void writeStaffModeConfig(String... lines) throws IOException {
        Files.write(tempDir.resolve("staff_mode.yml"),
                String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
