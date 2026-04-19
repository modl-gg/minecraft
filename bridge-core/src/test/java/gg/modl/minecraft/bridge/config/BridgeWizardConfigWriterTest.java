package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BridgeWizardConfigWriterTest {

    @TempDir
    Path tempDir;

    private final PluginLogger logger = new PluginLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
    };

    @Test
    void writesWizardProxySettingsIntoBridgeConfig() throws IOException {
        BootConfig bootConfig = new BootConfig();
        bootConfig.setMode(BootConfig.Mode.BRIDGE_ONLY);
        bootConfig.setWizardProxyHost(" 10.0.0.25 ");
        bootConfig.setWizardProxyPort(25595);

        BridgeWizardConfigWriter.writeBridgeOnlyConfig(tempDir, bootConfig, logger);

        BridgeConfig bridgeConfig = BridgeConfig.load(tempDir);
        assertEquals("10.0.0.25", bridgeConfig.getProxyHost());
        assertEquals(25595, bridgeConfig.getProxyPort());
    }

    @Test
    void skipsWritingWhenWizardProxyHostIsMissing() {
        BootConfig bootConfig = new BootConfig();
        bootConfig.setMode(BootConfig.Mode.BRIDGE_ONLY);
        bootConfig.setWizardProxyPort(25595);

        BridgeWizardConfigWriter.writeBridgeOnlyConfig(tempDir, bootConfig, logger);

        assertFalse(Files.exists(tempDir.resolve("bridge-config.yml")));
    }
}
