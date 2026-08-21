package gg.modl.minecraft.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.simplix.cirrus.velocity.CirrusVelocity;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.BootConfigMigrator;
import gg.modl.minecraft.core.boot.ConsoleInput;
import gg.modl.minecraft.core.boot.LibraryLoader;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.SetupWizard;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.boot.SyncPollingRate;
import gg.modl.minecraft.core.login.LoginPipeline;
import gg.modl.minecraft.core.packet.PacketInterceptionDecision;
import gg.modl.minecraft.core.packet.PacketInterceptionMode;
import gg.modl.minecraft.core.packet.PacketInterceptionPolicy;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.query.ProxyBridgeRuntime;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.config.yaml.CoreConfigBootstrap;
import io.github._4drian3d.signedvelocity.velocity.SignedVelocity;

import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import com.alessiodp.libby.VelocityLibraryManager;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.alessiodp.libby.logging.LogLevel;

@Plugin(id = PluginInfo.ID,
        name = PluginInfo.NAME,
        version = PluginInfo.VERSION,
        authors = { PluginInfo.AUTHOR },
        description = PluginInfo.DESCRIPTION,
        url = PluginInfo.URL,
        dependencies = {})
public final class VelocityPlugin {
    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private final PluginContainer plugin;
    private final ProxyServer server;
    private final Path folder;
    private final Logger logger;
    private final PluginLogger pluginLogger;

    private Map<String, Object> configuration;
    private PluginLoader pluginLoader;
    private ProxyBridgeRuntime bridgeRuntime;

    @Inject
    public VelocityPlugin(PluginContainer plugin, ProxyServer server, @DataDirectory Path folder, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.folder = folder;
        this.logger = logger;
        this.pluginLogger = new VelocityPluginLogger(logger);
    }

    @Subscribe
    public synchronized void onProxyInitialize(ProxyInitializeEvent event) {
        BootConfig bootConfig = loadOrCreateBootConfig();
        if (bootConfig == null) {
            return;
        }

        loadLibraries();
        createLocaleFiles();
        mergeDefaultConfigs();
        loadConfig();
        boolean packetInterception = initializePacketEvents();
        initSignedVelocity(packetInterception);

        String panelUrl = StartupClient.callStartupWithRetry(
                bootConfig.getApiKey(), bootConfig.isTestingApi(),
                new StartupRequest(PluginInfo.VERSION, "VELOCITY",
                        server.getVersion().getVersion(), server.getConfiguration().getShowMaxPlayers()),
                pluginLogger);
        if (panelUrl == null) {
            logger.error("Failed to connect to modl.gg. Check your API key and network connection.");
            return;
        }

        if (packetInterception) new CirrusVelocity(this, server).init();

        HttpManager httpManager = new HttpManager(
                bootConfig.getApiKey(),
                panelUrl,
                (Boolean) getNestedConfig("debug", false),
                bootConfig.isTestingApi(),
                (Boolean) getNestedConfig("server.query_mojang", false)
        );

        VelocityPlatform platform = new VelocityPlatform(this, this.server, logger, folder.toFile(), getConfigString("server.name", "Server 1"), pluginLogger, packetInterception);
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = SyncPollingRate.clamp(getConfigInt(SyncPollingRate.CONFIG_KEY, SyncPollingRate.DEFAULT_SECONDS));

        this.pluginLoader = new PluginLoader(platform, folder, chatMessageCache, httpManager, syncPollingRate);
        configureBridgeExecutor(platform, bootConfig, panelUrl);

        @SuppressWarnings("unchecked")
        List<String> mutedCommands = (List<String>) getNestedConfig("muted_commands", Collections.emptyList());

        CommandInterceptService commandInterceptService = new CommandInterceptService(
                pluginLoader.getCache(), pluginLoader.getFreezeService(), pluginLoader.getChatCommandLogService(),
                pluginLoader.getLocaleManager(), pluginLoader.getPunishmentMessageService(), mutedCommands);

        ProxyLoginFlow proxyLoginFlow = new ProxyLoginFlow(
                pluginLoader.getHttpClientHolder(), pluginLoader.getLoginCache(), pluginLoader.getLoginService(),
                pluginLoader.getIpEnrichmentService(),
                pluginLoader.getPendingIpLookupService(), LOGIN_TIMEOUT_SECONDS);

        LoginPipeline loginPipeline = new LoginPipeline(
                pluginLoader.getLoginService(), pluginLoader.getLoginCache(), pluginLoader.getPlayerSessionService());

        server.getEventManager().register(this, new JoinListener(
                pluginLoader.getCache(), logger, platform, loginPipeline, proxyLoginFlow,
                pluginLoader.getServerSwitchService(), pluginLoader.isDebugMode()));
        server.getEventManager().register(this, new ChatListener(
                pluginLoader.getChatService(), commandInterceptService));

        logger.info("Successfully booted modl.gg platform plugin!");
    }

    @Subscribe
    public synchronized void onProxyShutdown(ProxyShutdownEvent event) {
        if (bridgeRuntime != null) bridgeRuntime.shutdown();
        if (pluginLoader != null) pluginLoader.shutdown();
        terminatePacketEvents();
    }

    private void initSignedVelocity(boolean packetInterceptionEnabled) {
        if (server.getPluginManager().getPlugin("signedvelocity").isPresent()) {
            logger.info("[SignedVelocity] Using standalone SignedVelocity plugin");
            return;
        }

        SignedVelocity sv = new SignedVelocity(server, this, logger);
        sv.init();
        if (packetInterceptionEnabled) sv.enforceSecureChat();
        logger.info("[SignedVelocity] Embedded listeners registered");
    }

    private BootConfig loadOrCreateBootConfig() {
        try {
            if (BootConfig.exists(folder)) {
                BootConfig config = BootConfig.load(folder);
                if (config != null && config.isValid()) {
                    return config;
                }
            }

            Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                    folder, PlatformType.VELOCITY, pluginLogger);
            if (migrated.isPresent()) {
                return migrated.get();
            }

            logger.info("No configuration found. Starting setup wizard...");
            ConsoleInput input = ConsoleInput.system(pluginLogger);
            SetupWizard wizard = new SetupWizard(pluginLogger, input, PlatformType.VELOCITY);
            BootConfig config = wizard.run();

            if (config != null) {
                config.save(folder);
                return config;
            }

            logConfigurationError();
            return null;
        } catch (IOException e) {
            logger.error("Failed to load boot.yml: " + e.getMessage());
            return null;
        }
    }

    private void mergeDefaultConfigs() {
        CoreConfigBootstrap.updateBootConfig(folder, pluginLogger);
        CoreConfigBootstrap.updateRuntimeConfigs(folder, pluginLogger);
    }

    private void logConfigurationError() {
        logger.error("===============================================");
        logger.error("modl.gg CONFIGURATION ERROR");
        logger.error("===============================================");
        logger.error("Setup wizard failed or was cancelled.");
        logger.error("Delete boot.yml and restart to re-run the wizard,");
        logger.error("or configure boot.yml manually.");
        logger.error("Plugin initialization stopped due to invalid configuration.");
        logger.error("===============================================");
    }

    private void configureBridgeExecutor(VelocityPlatform platform, BootConfig bootConfig, String panelUrl) {
        bridgeRuntime = ProxyBridgeRuntime.startIfProxy(platform, pluginLoader, bootConfig, pluginLogger, panelUrl);
    }

    private boolean initializePacketEvents() {
        PacketInterceptionDecision decision = PacketInterceptionPolicy.decide(
                PacketInterceptionMode.fromConfig(getNestedConfig(PacketInterceptionPolicy.CONFIG_KEY, null)),
                pluginId -> server.getPluginManager().getPlugin(pluginId).isPresent());

        if (!decision.isEnabled()) {
            logger.warn("modl packet interception is off: {}", decision.getReason());
            return false;
        }

        try {
            PacketEvents.setAPI(VelocityPacketEventsBuilder.build(server, plugin, logger, folder));
            PacketEvents.getAPI().load();
            PacketEvents.getAPI().init();
            logger.debug("modl packet interception is on: {}", decision.getReason());
            return true;
        } catch (Throwable failure) {
            logger.warn("modl packet interception could not start on this proxy; proxy menus and secure-chat "
                    + "enforcement are off. Moderation is unaffected.", failure);
            terminatePacketEvents();
            return false;
        }
    }

    private void terminatePacketEvents() {
        if (PacketEvents.getAPI() == null) return;
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable failure) {
            logger.debug("PacketEvents termination failed: {}", failure.getMessage());
        } finally {
            PacketEvents.setAPI(null);
        }
    }

    private void loadLibraries() {
        VelocityLibraryManager<VelocityPlugin> libraryManager = new VelocityLibraryManager<>(
                this, logger, folder, server.getPluginManager());
        libraryManager.setLogLevel(LogLevel.WARN);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://nexus.modl.gg/repository/maven-releases/");
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.PROTO_DEPS_RELOCATED) loadLibrary(libraryManager, record);
        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.LAMP_COMMON);
        loadLibrary(libraryManager, Libraries.LAMP_BRIGADIER);
        loadLibrary(libraryManager, Libraries.LAMP_VELOCITY);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
        loadLibrary(libraryManager, Libraries.CIRRUS_VELOCITY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_VELOCITY);

        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_MINIMESSAGE);
    }

    private void loadLibrary(VelocityLibraryManager<VelocityPlugin> libraryManager, LibraryRecord record) {
        libraryManager.loadLibrary(LibraryLoader.toLibrary(record));
    }

    private void loadConfig() {
        try {
            if (!Files.exists(folder)) Files.createDirectories(folder);
            Path configFile = folder.resolve("config.yml");
            if (!Files.exists(configFile)) createDefaultConfig(configFile);

            Yaml yaml = new Yaml();
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                this.configuration = yaml.load(inputStream);
                if (this.configuration == null) {
                    logger.warn("Configuration file is empty, using defaults");
                    this.configuration = mapOf();
                }
            }
            logger.debug("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            this.configuration = mapOf();
        }
    }

    private void createDefaultConfig(Path configFile) throws IOException {
        try (InputStream defaultConfig = getClass().getResourceAsStream("/config.yml")) {
            if (defaultConfig != null) Files.copy(defaultConfig, configFile);
            else logger.warn("Default config resource not found in JAR");
        }
    }

    private String getConfigString(String path, String defaultValue) {
        Object value = getNestedConfig(path, defaultValue);
        return value instanceof String ? (String) value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Object getNestedConfig(String path, Object defaultValue) {
        if (configuration == null) return defaultValue;
        String[] keys = path.split("\\.");
        Object current = configuration;
        for (String key : keys) {
            if (current instanceof Map) current = ((Map<String, Object>) current).get(key);
            else return defaultValue;
        }
        return current != null ? current : defaultValue;
    }

    private int getConfigInt(String path, int defaultValue) {
        Object value = getNestedConfig(path, defaultValue);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return defaultValue;
    }

    private void createLocaleFiles() {
        try {
            Path localeDir = folder.resolve("locale");
            if (!Files.exists(localeDir)) Files.createDirectories(localeDir);

            Path enUsFile = localeDir.resolve("en_US.yml");
            if (Files.exists(enUsFile)) return;

            try (InputStream defaultLocale = getClass().getResourceAsStream("/locale/en_US.yml")) {
                if (defaultLocale != null) Files.copy(defaultLocale, enUsFile);
                else logger.warn("Default locale resource not found in JAR");
            }
        } catch (IOException e) {
            logger.error("Failed to create locale files", e);
        }
    }
}
