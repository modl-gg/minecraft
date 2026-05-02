package gg.modl.minecraft.bridge;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractBridgeComponent {

    protected final BridgePluginContext context;
    protected final String apiKey;
    protected final String backendUrl;
    protected final String panelUrl;
    protected final PluginLogger pluginLogger;
    protected final List<AntiCheatHook> hooks = new ArrayList<>();

    @Getter protected BridgeConfig bridgeConfig;
    @Getter protected BridgeLocaleManager localeManager;
    @Getter protected StaffModeConfig staffModeConfig;
    @Getter protected StatWipeHandler statWipeHandler;
    @Getter protected ViolationTracker violationTracker;
    @Getter protected AutoReporter autoReporter;
    @Getter protected BridgeQueryClient bridgeClient;
    @Getter protected ReplayService replayService;

    protected AbstractBridgeComponent(BridgePluginContext context, String apiKey,
                                      String backendUrl, String panelUrl, PluginLogger pluginLogger) {
        this.context = context;
        this.apiKey = apiKey;
        this.backendUrl = backendUrl;
        this.panelUrl = panelUrl;
        this.pluginLogger = pluginLogger;
    }

    public void enable(TicketCreator ticketCreator, boolean connectToProxy) {
        Path dataFolder = context.getDataFolder();
        Logger logger = context.getLogger();

        prepareBridgeConfig(dataFolder);
        prepareBridgeLocale(logger);
        prepareStaffModeConfig(dataFolder);
        startLifecycleServices(dataFolder, logger);
        initializePlatformHandlers();
        connectBridgeClientIfConfigured(connectToProxy, logger);
        initializeAutoReporting(logger, ticketCreator);
        registerRuntimeHooks();
    }

    public void disable() {
        onDisable();
        stopLifecycleServices();
        unregisterAntiCheatHooks();
    }

    private void prepareBridgeConfig(Path dataFolder) {
        if (!BridgeConfig.exists(dataFolder)) {
            context.saveDefaultResource("bridge-config.yml");
        }
        YamlMergeUtil.mergeWithDefaults("/bridge-config.yml",
                dataFolder.resolve("bridge-config.yml"), pluginLogger);

        try {
            bridgeConfig = BridgeConfig.load(dataFolder);
        } catch (IOException e) {
            pluginLogger.severe("[bridge] Failed to load bridge-config.yml: " + e.getMessage());
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.setApiKey(apiKey);
    }

    private void prepareBridgeLocale(Logger logger) {
        localeManager = new BridgeLocaleManager(logger);
    }

    private void prepareStaffModeConfig(Path dataFolder) {
        if (!dataFolder.resolve("staff_mode.yml").toFile().exists()) {
            context.saveDefaultResource("staff_mode.yml");
        }
        YamlMergeUtil.mergeWithDefaults("/staff_mode.yml",
                dataFolder.resolve("staff_mode.yml"), pluginLogger);
    }

    private void startLifecycleServices(Path dataFolder, Logger logger) {
        violationTracker = new ViolationTracker();
        violationTracker.startCleanupTask(context.getScheduler());

        statWipeHandler = new StatWipeHandler(logger, bridgeConfig, context.getPlayerProvider());

        staffModeConfig = new StaffModeConfig(dataFolder, logger);
    }

    private void initializePlatformHandlers() {
        initFreezeHandler(localeManager);
        initStaffModeHandler(bridgeConfig, localeManager, staffModeConfig);
    }

    private void connectBridgeClientIfConfigured(boolean connectToProxy, Logger logger) {
        if (connectToProxy && !isBlank(bridgeConfig.getProxyHost()) && !isBlank(bridgeConfig.getApiKey())) {
            bridgeClient = new BridgeQueryClient(
                    bridgeConfig.getProxyHost(),
                    bridgeConfig.getProxyPort(),
                    bridgeConfig.getApiKey(),
                    bridgeConfig.getServerName(),
                    logger,
                    context.getScheduler(),
                    createMessageHandler()
            );
            bridgeClient.connect();
            onBridgeClientCreated(bridgeClient);
        } else if (connectToProxy && isBlank(bridgeConfig.getProxyHost())) {
            pluginLogger.warning("[bridge] Bridge-only mode is enabled but proxy-host is empty; backend will not connect to proxy");
        } else if (connectToProxy && isBlank(bridgeConfig.getApiKey())) {
            pluginLogger.warning("[bridge] Bridge-only mode is enabled but api-key is empty; backend cannot authenticate to proxy");
        }
    }

    private void initializeAutoReporting(Logger logger, TicketCreator ticketCreator) {
        autoReporter = new AutoReporter(logger, bridgeConfig, ticketCreator, violationTracker);

        initReplayRecording(bridgeConfig);
        if (replayService != null) {
            autoReporter.setReplayService(replayService);
        }
    }

    private void registerRuntimeHooks() {
        registerAntiCheatHooks(hooks);
        registerPlatformEvents();

        if (bridgeClient != null) {
            registerProxyCommand(bridgeClient);
        }
    }

    private void stopLifecycleServices() {
        if (violationTracker != null) violationTracker.stopCleanupTask();
        if (bridgeClient != null) bridgeClient.shutdown();
    }

    private void unregisterAntiCheatHooks() {
        hooks.forEach(AntiCheatHook::unregister);
        hooks.clear();
    }

    protected abstract void initFreezeHandler(BridgeLocaleManager localeManager);

    protected abstract void initStaffModeHandler(BridgeConfig bridgeConfig,
                                                  BridgeLocaleManager localeManager,
                                                  StaffModeConfig staffModeConfig);

    protected abstract BridgeMessageHandler createMessageHandler();

    protected abstract void onBridgeClientCreated(BridgeQueryClient client);

    protected abstract void registerAntiCheatHooks(List<AntiCheatHook> hooks);

    protected abstract void initReplayRecording(BridgeConfig config);

    protected abstract void registerPlatformEvents();

    protected abstract void registerProxyCommand(BridgeQueryClient client);

    protected abstract void onDisable();

    protected static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            return host != null ? host : url.trim();
        } catch (Exception e) {
            String result = normalized.substring(normalized.indexOf("://") + 3);
            int slashIndex = result.indexOf('/');
            if (slashIndex > 0) result = result.substring(0, slashIndex);
            return result;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
