package gg.modl.minecraft.bridge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BridgeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsProxyConnectionFromCanonicalKeys() throws IOException {
        writeBridgeConfig(
                "proxy-host: \"10.0.0.25\"",
                "proxy-port: 25591"
        );

        BridgeConfig config = BridgeConfig.load(tempDir);

        assertEquals("10.0.0.25", config.getProxyHost());
        assertEquals(25591, config.getProxyPort());
    }

    @Test
    void loadsProxyConnectionFromLegacyHostAndPortKeys() throws IOException {
        writeBridgeConfig(
                "host: \"10.0.0.25\"",
                "port: 25591"
        );

        BridgeConfig config = BridgeConfig.load(tempDir);

        assertEquals("10.0.0.25", config.getProxyHost());
        assertEquals(25591, config.getProxyPort());
    }

    @Test
    void loadsProxyPortFromLegacyQueryPortKey() throws IOException {
        writeBridgeConfig(
                "proxy-host: \"10.0.0.25\"",
                "query-port: 25591"
        );

        BridgeConfig config = BridgeConfig.load(tempDir);

        assertEquals("10.0.0.25", config.getProxyHost());
        assertEquals(25591, config.getProxyPort());
    }

    @Test
    void defaultReplayPolicyIsOptInWhenKeysAreMissing() throws IOException {
        writeBridgeConfig(
                "proxy-host: \"10.0.0.25\"",
                "proxy-port: 25591"
        );

        BridgeConfig config = BridgeConfig.load(tempDir);

        assertFalse(config.isReplayEnabled());
        assertFalse(config.isReplayAutoRecord());
    }

    @Test
    void packagedBridgeConfigUsesSafeReplayDefaults() throws IOException {
        Map<String, Object> defaults = loadClasspathBridgeConfig();

        assertSafeReplayDefaults(defaults);
    }

    @Test
    void bridgeConfigResourceCopiesStayConsistent() throws IOException {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path rootDir = projectDir.getFileName().toString().equals("bridge-core")
                ? projectDir.getParent()
                : projectDir;

        Path bridgeCoreConfig = rootDir.resolve("bridge-core/src/main/resources/bridge-config.yml");
        Path fabricConfig = rootDir.resolve("platforms/fabric/src/main/resources/bridge-config.yml");
        Path spigotConfig = rootDir.resolve("platforms/spigot/src/main/resources/bridge-config.yml");

        String bridgeCoreContent = readString(bridgeCoreConfig);
        assertEquals(bridgeCoreContent, readString(fabricConfig));
        assertEquals(bridgeCoreContent, readString(spigotConfig));

        assertSafeReplayDefaults(loadYaml(bridgeCoreConfig));
    }

    @Test
    void reloadReportsRestartRequiredSettingsWithoutApplyingThem() {
        BridgeConfig current = BridgeConfig.builder().build();
        BridgeConfig reloaded = BridgeConfig.builder()
                .proxyHost("10.0.0.9")
                .proxyPort(26000)
                .serverName("Hub")
                .replayEnabled(true)
                .replayAutoRecord(true)
                .build();

        assertEquals(Arrays.asList("replay-enabled", "replay-auto-record", "proxy-host", "proxy-port", "server-name"),
                current.settingsNeedingRestart(reloaded));

        current.applyReloadableSettings(reloaded);

        assertEquals("", current.getProxyHost());
        assertEquals(25590, current.getProxyPort());
        assertEquals("Server 1", current.getServerName());
        assertFalse(current.isReplayEnabled());
        assertFalse(current.isReplayAutoRecord());
    }

    @Test
    void everySavedSettingIsEitherAppliedOnReloadOrReportedAsRestartRequired() throws IOException {
        BridgeConfig current = BridgeConfig.builder().build();
        Map<String, Object> defaults = savedYaml(current, "defaults");

        BridgeConfig reloaded = fullyChangedConfig();
        Map<String, Object> changed = savedYaml(reloaded, "changed");

        List<String> restartRequired = current.settingsNeedingRestart(reloaded);
        current.applyReloadableSettings(reloaded);
        Map<String, Object> applied = savedYaml(current, "applied");

        for (String key : changed.keySet()) {
            assertNotEquals(defaults.get(key), changed.get(key),
                    key + " must differ from its default for this test to cover it");
            if (restartRequired.contains(key)) {
                assertEquals(defaults.get(key), applied.get(key), key + " is restart-required but was applied live");
            } else {
                assertEquals(changed.get(key), applied.get(key), key + " is neither applied nor restart-required");
            }
        }
    }

    private Map<String, Object> savedYaml(BridgeConfig config, String directoryName) throws IOException {
        Path dir = tempDir.resolve(directoryName);
        config.save(dir);
        return loadYaml(dir.resolve("bridge-config.yml"));
    }

    private static BridgeConfig fullyChangedConfig() {
        Map<String, Integer> thresholds = new LinkedHashMap<>();
        thresholds.put("default", 3);
        return BridgeConfig.builder()
                .proxyHost("10.0.0.9")
                .proxyPort(26000)
                .statWipeCommands(Arrays.asList("wipe {player}"))
                .anticheatName("Grim")
                .anticheatHookEnabled(false)
                .serverName("Hub")
                .reportCooldown(5)
                .reportViolationThresholds(thresholds)
                .debug(true)
                .replayEnabled(true)
                .replayAutoRecord(true)
                .replayBufferDuration(60)
                .replayMaxDuration(150)
                .replayRadius(32)
                .replayMoveThrottle(25)
                .replaySaveLocal(false)
                .replayLocalTtl(30)
                .build();
    }

    private void writeBridgeConfig(String... lines) throws IOException {
        Files.write(tempDir.resolve("bridge-config.yml"),
                String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadClasspathBridgeConfig() throws IOException {
        try (InputStream input = BridgeConfigTest.class.getResourceAsStream("/bridge-config.yml")) {
            assertNotNull(input);
            return (Map<String, Object>) new Yaml().load(input);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml().load(input);
        }
    }

    private static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private void assertSafeReplayDefaults(Map<String, Object> defaults) {
        assertEquals(Boolean.FALSE, defaults.get("replay-enabled"));
        assertEquals(Boolean.FALSE, defaults.get("replay-auto-record"));
        assertEquals(Boolean.TRUE, defaults.get("replay-save-local"));
    }
}
