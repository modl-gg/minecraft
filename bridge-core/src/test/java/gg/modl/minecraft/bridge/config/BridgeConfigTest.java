package gg.modl.minecraft.bridge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private void writeBridgeConfig(String... lines) throws IOException {
        Files.write(tempDir.resolve("bridge-config.yml"),
                String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }
}
