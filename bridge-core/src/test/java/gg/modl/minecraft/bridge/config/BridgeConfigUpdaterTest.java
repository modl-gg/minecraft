package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.core.config.yaml.ConfigUpdate;
import gg.modl.minecraft.core.config.yaml.ConfigUpdater;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeConfigUpdaterTest {

    private static final PluginLogger LOGGER = new PluginLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
    };

    @TempDir
    Path dataFolder;

    @Test
    void addsTheAnticheatToggleToAnOlderBridgeConfig() throws IOException {
        write("bridge-config.yml", "proxy-host: \"10.0.0.5\"\nanticheat-name: \"Grim\"\n");

        ConfigUpdate update = ConfigUpdater.update(BridgeManagedConfigs.BRIDGE_CONFIG, dataFolder, LOGGER);

        assertTrue(update.getAddedPaths().contains("anticheat-hook-enabled"));
        Map<?, ?> loaded = load("bridge-config.yml");
        assertEquals(true, loaded.get("anticheat-hook-enabled"));
        assertEquals("10.0.0.5", loaded.get("proxy-host"));
        assertEquals("Grim", loaded.get("anticheat-name"));
    }

    @Test
    void keepsCustomViolationThresholdsIntact() throws IOException {
        write("bridge-config.yml", "report-violation-threshold:\n  badpackets: 25\n");

        ConfigUpdater.update(BridgeManagedConfigs.BRIDGE_CONFIG, dataFolder, LOGGER);

        Map<?, ?> thresholds = (Map<?, ?>) load("bridge-config.yml").get("report-violation-threshold");
        assertEquals(1, thresholds.size());
        assertEquals(25, thresholds.get("badpackets"));
        assertFalse(thresholds.containsKey("default"));
    }

    @Test
    void doesNotResurrectHotbarSlotsTheOwnerRemoved() throws IOException {
        write("staff_mode.yml", "vanish_on_enable: false\nstaff_hotbar:\n  0:\n    item: \"minecraft:stick\"\n");

        ConfigUpdater.update(BridgeManagedConfigs.STAFF_MODE, dataFolder, LOGGER);

        Map<?, ?> hotbar = (Map<?, ?>) load("staff_mode.yml").get("staff_hotbar");
        assertEquals(1, hotbar.size());
        assertEquals(false, load("staff_mode.yml").get("vanish_on_enable"));
    }

    @Test
    void doesNotRestoreScoreboardLinesTheOwnerTrimmed() throws IOException {
        write("staff_mode.yml", "staff_scoreboard:\n  enabled: true\n  lines:\n    - \"just one\"\n");

        ConfigUpdater.update(BridgeManagedConfigs.STAFF_MODE, dataFolder, LOGGER);

        Map<?, ?> scoreboard = (Map<?, ?>) load("staff_mode.yml").get("staff_scoreboard");
        assertEquals(1, ((java.util.List<?>) scoreboard.get("lines")).size());
        assertTrue(scoreboard.containsKey("title"));
    }

    @Test
    void shippedDefaultsAreStableAcrossRepeatedRuns() {
        ConfigUpdater.update(BridgeManagedConfigs.BRIDGE_CONFIG, dataFolder, LOGGER);
        ConfigUpdater.update(BridgeManagedConfigs.STAFF_MODE, dataFolder, LOGGER);

        assertFalse(ConfigUpdater.update(BridgeManagedConfigs.BRIDGE_CONFIG, dataFolder, LOGGER).isChanged());
        assertFalse(ConfigUpdater.update(BridgeManagedConfigs.STAFF_MODE, dataFolder, LOGGER).isChanged());
    }

    @Test
    void anUpdatedFileStillLoadsIntoBridgeConfig() throws IOException {
        write("bridge-config.yml", "proxy-host: \"10.0.0.5\"\n");

        ConfigUpdater.update(BridgeManagedConfigs.BRIDGE_CONFIG, dataFolder, LOGGER);

        BridgeConfig config = BridgeConfig.load(dataFolder);
        assertEquals("10.0.0.5", config.getProxyHost());
        assertTrue(config.isAnticheatHookEnabled());
    }

    private void write(String name, String content) throws IOException {
        Files.write(dataFolder.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    private Map<?, ?> load(String name) throws IOException {
        return new Yaml().load(new String(Files.readAllBytes(dataFolder.resolve(name)), StandardCharsets.UTF_8));
    }
}
