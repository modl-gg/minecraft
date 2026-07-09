package gg.modl.minecraft.core.config;

import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigSourceTest {

    @TempDir
    Path tempDir;

    private final PluginLogger logger = new PluginLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
    };

    @Test
    void loadsRootValuesAndReturnsDefensiveMaps() throws IOException {
        write(tempDir.resolve("config.yml"),
                "locale: de",
                "commands:",
                "  replay: replay|record",
                "locale_config:",
                "  timezone: UTC"
        );

        RuntimeConfigSource source = RuntimeConfigSource.load(tempDir, logger);

        assertEquals("de", source.root().get("locale"));
        assertEquals("replay|record", source.rootSection("commands").get("replay"));
        assertEquals("UTC", source.rootSection("locale_config").get("timezone"));
        assertTrue(source.rootSection("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> source.root().put("locale", "en_US"));
        assertThrows(UnsupportedOperationException.class, () -> source.rootSection("commands").put("replay", "replay"));
    }

    @Test
    void sectionFileTakesPrecedenceOverConfigYmlFallback() throws IOException {
        write(tempDir.resolve("config.yml"),
                "staff_chat:",
                "  enabled: false",
                "  prefix: \"!\""
        );
        write(tempDir.resolve("staff_chat.yml"),
                "enabled: true",
                "prefix: \"#\""
        );

        RuntimeConfigSource source = RuntimeConfigSource.load(tempDir, logger);
        Map<String, Object> section = source.section("staff_chat.yml", "staff_chat");

        assertEquals(true, section.get("enabled"));
        assertEquals("#", section.get("prefix"));
    }

    @Test
    void sectionFallsBackToConfigYmlWhenDedicatedFileIsAbsent() throws IOException {
        write(tempDir.resolve("config.yml"),
                "chat_management:",
                "  clear_lines: 250"
        );

        RuntimeConfigSource source = RuntimeConfigSource.load(tempDir, logger);

        assertEquals(250, source.section("chat_management.yml", "chat_management").get("clear_lines"));
    }

    @Test
    void loadReflectsCurrentConfigFileContents() throws IOException {
        write(tempDir.resolve("config.yml"), "locale: en_US");
        RuntimeConfigSource first = RuntimeConfigSource.load(tempDir, logger);

        write(tempDir.resolve("config.yml"), "locale: es");
        RuntimeConfigSource second = RuntimeConfigSource.load(tempDir, logger);

        assertEquals("en_US", first.root().get("locale"));
        assertEquals("es", second.root().get("locale"));
    }

    private void write(Path path, String... lines) throws IOException {
        Files.write(path, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
