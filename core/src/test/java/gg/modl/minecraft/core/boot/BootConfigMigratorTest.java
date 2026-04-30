package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootConfigMigratorTest {

    @TempDir
    Path tempDir;

    private final PluginLogger logger = new PluginLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
    };

    @Test
    void migratesFabricWithBridgeHostAsBridgeOnlyBackend() throws IOException {
        writeLegacyConfig(
                "api:",
                "  key: \"1234567890123456789012345678901234567890\"",
                "  url: \"https://example.modl.gg\"",
                "bridge:",
                "  host: \"10.0.0.25\"",
                "  port: 25591"
        );

        Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                tempDir, PlatformType.FABRIC, logger);

        assertTrue(migrated.isPresent());
        assertEquals(BootConfig.Mode.BRIDGE_ONLY, migrated.get().getMode());
        assertEquals("10.0.0.25", migrated.get().getWizardProxyHost());
        assertEquals(25591, migrated.get().getWizardProxyPort());

        BootConfig reloaded = BootConfig.load(tempDir);
        assertEquals(BootConfig.Mode.BRIDGE_ONLY, reloaded.getMode());
        assertEquals("10.0.0.25", reloaded.getWizardProxyHost());
        assertEquals(25591, reloaded.getWizardProxyPort());
    }

    @Test
    void migratesVelocityAsProxy() throws IOException {
        writeLegacyConfig(
                "api:",
                "  key: \"1234567890123456789012345678901234567890\"",
                "  url: \"https://example.modl.gg\"",
                "bridge:",
                "  host: \"10.0.0.25\"",
                "  port: 25591"
        );

        Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                tempDir, PlatformType.VELOCITY, logger);

        assertTrue(migrated.isPresent());
        assertEquals(BootConfig.Mode.PROXY, migrated.get().getMode());
        assertEquals(25591, migrated.get().getBridgePort());
    }

    private void writeLegacyConfig(String... lines) throws IOException {
        Files.write(tempDir.resolve("config.yml"),
                String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
