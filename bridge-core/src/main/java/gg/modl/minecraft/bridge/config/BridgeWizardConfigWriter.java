package gg.modl.minecraft.bridge.config;

import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.util.PluginLogger;

import java.io.IOException;
import java.nio.file.Path;

public final class BridgeWizardConfigWriter {
    private BridgeWizardConfigWriter() {}

    public static void writeBridgeOnlyConfig(Path dataFolder, BootConfig bootConfig, PluginLogger logger) {
        if (bootConfig == null || bootConfig.getMode() != BootConfig.Mode.BRIDGE_ONLY) return;

        String proxyHost = bootConfig.getWizardProxyHost();
        if (proxyHost == null) return;

        String trimmedProxyHost = proxyHost.trim();
        if (trimmedProxyHost.isEmpty()) return;

        try {
            BridgeConfig bridgeConfig = BridgeConfig.load(dataFolder);
            bridgeConfig.updateProxyConnection(trimmedProxyHost, bootConfig.getWizardProxyPort());
            bridgeConfig.save(dataFolder);
        } catch (IOException e) {
            logger.warning("Failed to write bridge-config.yml: " + e.getMessage());
        }
    }
}
