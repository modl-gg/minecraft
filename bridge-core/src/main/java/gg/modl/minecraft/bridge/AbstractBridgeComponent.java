package gg.modl.minecraft.bridge;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.BridgeManagedConfigs;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.bridge.resource.BridgeYamlResource;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBridgeComponent {

    protected final BridgePluginContext context;
    protected final String apiKey;
    protected final String backendUrl;
    protected final String panelUrl;
    protected final PluginLogger pluginLogger;
    protected final List<AntiCheatHook> hooks = new ArrayList<>();

    protected boolean bridgeOnly;
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

    public void enableStandalone(TicketCreator ticketCreator) {
        startBridge(ticketCreator, false);
    }

    public void enableBridgeOnly(TicketCreator ticketCreator) {
        startBridge(ticketCreator, true);
    }

    private void startBridge(TicketCreator ticketCreator, boolean bridgeOnly) {
        this.bridgeOnly = bridgeOnly;
        Path dataFolder = context.getDataFolder();

        prepareBridgeConfig(dataFolder);
        prepareBridgeLocale();
        prepareStaffModeConfig();
        startLifecycleServices(dataFolder);
        initializePlatformHandlers();
        connectBridgeClientIfConfigured(bridgeOnly);
        initializeAutoReporting(ticketCreator);
        registerRuntimeHooks();
    }

    public void disable() {
        onDisable();
        stopLifecycleServices();
        unregisterAntiCheatHooks();
    }

    public BridgeScheduler getScheduler() {
        return context.getScheduler();
    }

    private void prepareBridgeConfig(Path dataFolder) {
        BridgeYamlResource.ensureDefaultFile(context, BridgeManagedConfigs.BRIDGE_CONFIG, pluginLogger);

        try {
            bridgeConfig = BridgeConfig.load(dataFolder);
        } catch (IOException e) {
            pluginLogger.severe("[bridge] Failed to load bridge-config.yml: " + e.getMessage());
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.setApiKey(apiKey);
    }

    private void prepareBridgeLocale() {
        localeManager = new BridgeLocaleManager(pluginLogger);
    }

    private void prepareStaffModeConfig() {
        BridgeYamlResource.ensureDefaultFile(context, BridgeManagedConfigs.STAFF_MODE, pluginLogger);
    }

    private void startLifecycleServices(Path dataFolder) {
        violationTracker = new ViolationTracker();
        violationTracker.startCleanupTask(context.getScheduler());

        statWipeHandler = new StatWipeHandler(pluginLogger, bridgeConfig, context.getPlayerProvider());

        staffModeConfig = StaffModeConfig.load(dataFolder, pluginLogger);
    }

    private void initializePlatformHandlers() {
        initFreezeHandler(localeManager);
        initStaffModeHandler(bridgeConfig, localeManager, staffModeConfig);
    }

    private void connectBridgeClientIfConfigured(boolean bridgeOnly) {
        if (!bridgeOnly) {
            return;
        }

        boolean proxyHostMissing = isBlank(bridgeConfig.getProxyHost());
        boolean apiKeyMissing = isBlank(bridgeConfig.getApiKey());

        if (proxyHostMissing) {
            pluginLogger.warning("[bridge] Bridge-only mode is enabled but proxy-host is empty; backend will not connect to proxy");
            return;
        }
        if (apiKeyMissing) {
            pluginLogger.warning("[bridge] Bridge-only mode is enabled but api-key is empty; backend cannot authenticate to proxy");
            return;
        }

        bridgeClient = new BridgeQueryClient(
                bridgeConfig.getProxyHost(),
                bridgeConfig.getProxyPort(),
                bridgeConfig.getApiKey(),
                bridgeConfig.getServerName(),
                pluginLogger,
                context.getScheduler(),
                createMessageHandler()
        );
        bridgeClient.connect();
        onBridgeClientCreated(bridgeClient);
    }

    private void initializeAutoReporting(TicketCreator ticketCreator) {
        autoReporter = new AutoReporter(pluginLogger, bridgeConfig, ticketCreator, violationTracker);

        initReplayRecording(bridgeConfig);
        if (replayService != null) {
            autoReporter.setReplayService(replayService);
        }
    }

    private void registerRuntimeHooks() {
        applyAntiCheatHookSetting();
        registerPlatformEvents();
        registerBridgeCommand();

        if (bridgeClient != null) {
            registerProxyCommand(bridgeClient);
        }
    }

    private void stopLifecycleServices() {
        if (violationTracker != null) violationTracker.stopCleanupTask();
        if (bridgeClient != null) bridgeClient.shutdown();
    }

    public final BridgeReloadResult reload() {
        Path dataFolder = context.getDataFolder();
        List<String> failures = new ArrayList<>();
        List<String> restartRequired = new ArrayList<>();

        BridgeYamlResource.ensureDefaultFile(context, BridgeManagedConfigs.BRIDGE_CONFIG, pluginLogger);
        BridgeYamlResource.ensureDefaultFile(context, BridgeManagedConfigs.STAFF_MODE, pluginLogger);

        try {
            BridgeConfig reloaded = BridgeConfig.load(dataFolder);
            restartRequired.addAll(bridgeConfig.settingsNeedingRestart(reloaded));
            bridgeConfig.applyReloadableSettings(reloaded);
        } catch (IOException | RuntimeException e) {
            pluginLogger.warning("[bridge] Failed to reload bridge-config.yml", e);
            failures.add("bridge-config.yml");
        }

        try {
            staffModeConfig.reload(dataFolder);
        } catch (RuntimeException e) {
            pluginLogger.warning("[bridge] Failed to reload staff_mode.yml", e);
            failures.add("staff_mode.yml");
        }

        try {
            applyAntiCheatHookSetting();
        } catch (RuntimeException e) {
            pluginLogger.warning("[bridge] Failed to reapply the anticheat hooks", e);
            failures.add("anticheat-hooks");
        }

        return new BridgeReloadResult(failures, restartRequired);
    }

    protected final void applyAntiCheatHookSetting() {
        unregisterAntiCheatHooks();
        if (!bridgeConfig.isAnticheatHookEnabled()) {
            pluginLogger.info("[bridge] Anticheat auto-reporting is disabled by anticheat-hook-enabled");
            return;
        }
        registerAntiCheatHooks(hooks);
    }

    public final boolean isAnticheatHookEnabled() {
        return bridgeConfig != null && bridgeConfig.isAnticheatHookEnabled();
    }

    private void unregisterAntiCheatHooks() {
        hooks.forEach(AntiCheatHook::unregister);
        hooks.clear();
    }

    protected abstract void registerBridgeCommand();

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
