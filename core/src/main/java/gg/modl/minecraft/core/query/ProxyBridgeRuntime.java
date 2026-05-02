package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.util.PluginLogger;

public final class ProxyBridgeRuntime {
    private final BridgeServer bridgeServer;

    private ProxyBridgeRuntime(BridgeServer bridgeServer) {
        this.bridgeServer = bridgeServer;
    }

    public static ProxyBridgeRuntime startIfProxy(
            Platform platform,
            PluginLoader pluginLoader,
            BootConfig bootConfig,
            PluginLogger pluginLogger,
            String panelUrl) {
        if (bootConfig.getMode() != BootConfig.Mode.PROXY) return null;

        int bridgePort = bootConfig.getBridgePort();
        String apiKey = bootConfig.getApiKey();

        BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                platform, pluginLoader.getLocaleManager(), pluginLoader.getFreezeService(),
                pluginLoader.getStaffModeService(), pluginLoader.getVanishService(),
                pluginLoader.getHttpClient(), pluginLogger);

        BridgeServer bridgeServer = new BridgeServer(bridgePort, apiKey, dispatcher, pluginLogger, panelUrl);
        bridgeServer.start();

        pluginLoader.getSyncService().setStatWipeExecutor(bridgeServer);
        pluginLoader.getBridgeService().setExecutor(bridgeServer);

        BridgeReplayService bridgeReplayService = new BridgeReplayService(bridgeServer, pluginLogger);
        dispatcher.setBridgeReplayService(bridgeReplayService);
        platform.setReplayService(bridgeReplayService);

        return new ProxyBridgeRuntime(bridgeServer);
    }

    public void shutdown() {
        bridgeServer.shutdown();
    }
}
