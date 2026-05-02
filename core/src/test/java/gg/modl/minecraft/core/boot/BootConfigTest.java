package gg.modl.minecraft.core.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsExistingModeAliases() {
        assertEquals(BootConfig.Mode.BRIDGE_ONLY, BootConfig.Mode.fromString("bridge"));
        assertEquals(BootConfig.Mode.BRIDGE_ONLY, BootConfig.Mode.fromString("bridge-only"));
        assertEquals(BootConfig.Mode.PROXY, BootConfig.Mode.fromString("proxy"));
        assertEquals(BootConfig.Mode.STANDALONE, BootConfig.Mode.fromString("unknown"));
        assertEquals(BootConfig.Mode.STANDALONE, BootConfig.Mode.fromString(null));
    }

    @Test
    void mapsProxyBootConfigWithCurrentKeySet() {
        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.PROXY);
        config.setApiKey("proxy-key");
        config.setTestingApi(true);
        config.setProxyType("velocity");
        config.setBridgePort(25591);
        config.setWizardProxyHost("10.0.0.25");
        config.setWizardProxyPort(25592);

        Map<String, Object> map = BootConfigYaml.toMap(config);

        assertEquals("proxy", map.get("mode"));
        assertEquals("proxy-key", map.get("api-key"));
        assertEquals(true, map.get("testing-api"));
        assertEquals("velocity", map.get("proxy-type"));
        assertEquals(25591, map.get("bridge-port"));
        assertFalse(map.containsKey("proxy-host"));
        assertFalse(map.containsKey("proxy-port"));
    }

    @Test
    void mapsBridgeOnlyProxyTargetOnlyWhenHostIsNonBlank() {
        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.BRIDGE_ONLY);
        config.setApiKey("backend-key");
        config.setWizardProxyHost("  10.0.0.25  ");
        config.setWizardProxyPort(25592);

        Map<String, Object> map = BootConfigYaml.toMap(config);

        assertEquals("bridge-only", map.get("mode"));
        assertEquals("10.0.0.25", map.get("proxy-host"));
        assertEquals(25592, map.get("proxy-port"));
        assertFalse(map.containsKey("bridge-port"));

        config.setWizardProxyHost(" ");
        Map<String, Object> blankHostMap = BootConfigYaml.toMap(config);
        assertFalse(blankHostMap.containsKey("proxy-host"));
        assertFalse(blankHostMap.containsKey("proxy-port"));
    }

    @Test
    void savePreservesModeSpecificOutputFields() throws IOException {
        BootConfig proxy = new BootConfig();
        proxy.setMode(BootConfig.Mode.PROXY);
        proxy.setApiKey("proxy-key");
        proxy.setBridgePort(25591);
        proxy.setWizardProxyHost("10.0.0.25");
        proxy.setWizardProxyPort(25592);

        proxy.save(tempDir);
        String proxyContent = readBootConfig();

        assertTrue(proxyContent.contains("mode: proxy\n"));
        assertTrue(proxyContent.contains("api-key: proxy-key\n"));
        assertTrue(proxyContent.contains("testing-api: false\n"));
        assertTrue(proxyContent.contains("bridge-port: 25591\n"));
        assertFalse(proxyContent.contains("proxy-host:"));
        assertFalse(proxyContent.contains("proxy-port:"));

        BootConfig bridgeOnly = new BootConfig();
        bridgeOnly.setMode(BootConfig.Mode.BRIDGE_ONLY);
        bridgeOnly.setApiKey("backend-key");
        bridgeOnly.setWizardProxyHost("  10.0.0.25  ");
        bridgeOnly.setWizardProxyPort(25592);

        bridgeOnly.save(tempDir);
        String bridgeOnlyContent = readBootConfig();

        assertTrue(bridgeOnlyContent.contains("mode: bridge-only\n"));
        assertTrue(bridgeOnlyContent.contains("proxy-host: 10.0.0.25\n"));
        assertTrue(bridgeOnlyContent.contains("proxy-port: 25592\n"));
        assertFalse(bridgeOnlyContent.contains("bridge-port:"));
    }

    @Test
    void saveTemplatePreservesCurrentVisibleOutput() throws IOException {
        BootConfig.saveTemplate(tempDir);

        assertEquals(BootConfigYaml.template(), readBootConfig());
        assertEquals("# modl.gg Boot Configuration\n"
                + "# Edit this file and restart the server.\n"
                + "# To register a new server, visit https://modl.gg/register\n"
                + "#\n"
                + "# mode: standalone | bridge-only | proxy\n"
                + "mode: proxy\n"
                + "\n"
                + "# Your API key from the modl.gg panel\n"
                + "api-key: \"your-api-key-here\"\n"
                + "\n"
                + "# Uncomment to use the testing API (api.modl.top)\n"
                + "# testing-api: true\n"
                + "\n"
                + "# Proxy connection target (bridge-only mode)\n"
                + "# proxy-host: \"127.0.0.1\"\n"
                + "# proxy-port: 25590\n"
                + "\n"
                + "# Bridge listen port (proxy mode only — backends connect to this port)\n"
                + "# bridge-port: 25590\n", readBootConfig());
    }

    private String readBootConfig() throws IOException {
        return new String(Files.readAllBytes(tempDir.resolve("boot.yml")), StandardCharsets.UTF_8);
    }
}
