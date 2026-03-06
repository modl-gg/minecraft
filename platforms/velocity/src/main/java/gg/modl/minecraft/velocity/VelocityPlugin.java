package gg.modl.minecraft.velocity;

import co.aikar.commands.VelocityCommandManager;
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
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.query.BridgeMessageDispatcher;
import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import net.byteflux.libby.Library;
import net.byteflux.libby.VelocityLibraryManager;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Plugin(id = PluginInfo.ID,
        name = PluginInfo.NAME,
        version = PluginInfo.VERSION,
        authors = { PluginInfo.AUTHOR },
        description = PluginInfo.DESCRIPTION,
        url = PluginInfo.URL)
public final class VelocityPlugin {
    private static final Yaml yaml = new Yaml();
    private static final String PLACEHOLDER_API_URL = "https://yourserver.modl.gg";
    private static final String DEFAULT_BRIDGE_NAME = "bridge";
    private static final int DEFAULT_BRIDGE_PORT = 25590;
    private static final int MIN_SYNC_POLLING_RATE = 1;
    private static final int DEFAULT_SYNC_POLLING_RATE = 2;
    private static final int BSTATS_PLUGIN_ID = 29830;

    private final PluginContainer plugin;
    private final ProxyServer server;
    private final Path folder;
    private final Logger logger;
    private final Metrics.Factory metrics;
    private final PluginLogger pluginLogger;

    private Map<String, Object> configuration;
    private PluginLoader pluginLoader;
    private QueryStatWipeExecutor queryStatWipeExecutor;

    @Inject
    public VelocityPlugin(PluginContainer plugin, ProxyServer server, @DataDirectory Path folder, Logger logger, Metrics.Factory metrics) {
        this.plugin = plugin;
        this.server = server;
        this.folder = folder;
        this.logger = logger;
        this.metrics = metrics;
        this.pluginLogger = new PluginLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warning(String message) { logger.warn(message); }
            @Override public void severe(String message) { logger.error(message); }
            @Override public void debug(String message) { logger.debug(message); }
        };
    }

    @Subscribe
    public synchronized void onProxyInitialize(ProxyInitializeEvent event) {
        loadLibraries();
        initializePacketEvents();
        loadConfig();
        createLocaleFiles();
        mergeDefaultConfigs();

        String apiUrl = getConfigString("api.url", PLACEHOLDER_API_URL);
        if (PLACEHOLDER_API_URL.equals(apiUrl)) {
            logConfigurationError();
            return;
        }

        VelocityCommandManager commandManager = new VelocityCommandManager(this.server, this);
        new CirrusVelocity(this, server).init();

        HttpManager httpManager = new HttpManager(
                getConfigString("api.key", "your-api-key-here"),
                apiUrl,
                (Boolean) getNestedConfig("api.debug", false),
                (Boolean) getNestedConfig("api.testing-api", false),
                (Boolean) getNestedConfig("server.query_mojang", false)
        );

        VelocityPlatform platform = new VelocityPlatform(this.server, commandManager, logger, folder.toFile(), getConfigString("server.name", "Server 1"));
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = Math.max(MIN_SYNC_POLLING_RATE, getConfigInt("sync.polling_rate", DEFAULT_SYNC_POLLING_RATE));

        this.pluginLoader = new PluginLoader(platform, folder, chatMessageCache, httpManager, syncPollingRate);
        configureBridgeExecutor(platform, httpManager);

        @SuppressWarnings("unchecked")
        List<String> mutedCommands = (List<String>) getNestedConfig("muted_commands", Collections.emptyList());

        server.getEventManager().register(this, new JoinListener(
                pluginLoader.getHttpClientHolder(), pluginLoader.getCache(), logger,
                pluginLoader.getChatMessageCache(), platform, pluginLoader.getSyncService(),
                pluginLoader.getLocaleManager(), pluginLoader.isDebugMode(),
                pluginLoader.getStaffChatService(), pluginLoader.getChatManagementService(),
                pluginLoader.getMaintenanceService(), pluginLoader.getFreezeService(),
                pluginLoader.getNetworkChatInterceptService(), pluginLoader.getStaff2faService(),
                pluginLoader.getVanishService(), pluginLoader.getStaffModeService(),
                pluginLoader.getBridgeService()));
        server.getEventManager().register(this, new ChatListener(
                platform, pluginLoader.getCache(), pluginLoader.getChatMessageCache(),
                pluginLoader.getLocaleManager(), mutedCommands,
                pluginLoader.getStaffChatService(), pluginLoader.getChatManagementService(),
                pluginLoader.getFreezeService(), pluginLoader.getNetworkChatInterceptService(),
                pluginLoader.getChatCommandLogService(),
                pluginLoader.getConfigManager().getStaffChatConfig()));

        metrics.make(this, BSTATS_PLUGIN_ID);
    }

    @Subscribe
    public synchronized void onProxyShutdown(ProxyShutdownEvent event) {
        if (queryStatWipeExecutor != null) queryStatWipeExecutor.shutdown();
        if (pluginLoader != null) pluginLoader.shutdown();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }

    private void mergeDefaultConfigs() {
        YamlMergeUtil.mergeWithDefaults("/config.yml", folder.resolve("config.yml"), pluginLogger);
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml", folder.resolve("locale/en_US.yml"), pluginLogger);
    }

    private void logConfigurationError() {
        logger.error("===============================================");
        logger.error("modl.gg CONFIGURATION ERROR");
        logger.error("===============================================");
        logger.error("You must configure your API URL in config.yml!");
        logger.error("Please set 'api.url' to your actual modl.gg panel URL.");
        logger.error("Example: https://yourserver.modl.gg");
        logger.error("Plugin initialization stopped due to invalid configuration.");
        logger.error("===============================================");
    }

    private void configureBridgeExecutor(VelocityPlatform platform, HttpManager httpManager) {
        String bridgeHost = getConfigString("bridge.host", "");
        if (bridgeHost.isEmpty()) return;

        int bridgePort = getConfigInt("bridge.port", DEFAULT_BRIDGE_PORT);
        String apiKey = getConfigString("api.key", "your-api-key-here");
        queryStatWipeExecutor = new QueryStatWipeExecutor(pluginLogger, httpManager.isDebugHttp());
        queryStatWipeExecutor.addBridge(DEFAULT_BRIDGE_NAME, bridgeHost, bridgePort, apiKey);
        pluginLoader.getSyncService().setStatWipeExecutor(queryStatWipeExecutor);

        BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                platform, pluginLoader.getLocaleManager(), pluginLoader.getFreezeService(),
                pluginLoader.getStaffModeService(), pluginLoader.getVanishService(), pluginLogger);
        queryStatWipeExecutor.setBridgeMessageDispatcher(dispatcher);
        pluginLoader.getBridgeService().setExecutor(queryStatWipeExecutor);
    }

    private void initializePacketEvents() {
        PacketEvents.setAPI(VelocityPacketEventsBuilder.build(server, plugin, logger, folder));
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().init();
        logger.info("PacketEvents initialized successfully");
    }

    private void loadLibraries() {
        VelocityLibraryManager<VelocityPlugin> libraryManager = new VelocityLibraryManager<>(
                logger, folder, server.getPluginManager(), this);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_VELOCITY);
        loadLibrary(libraryManager, Libraries.CIRRUS_VELOCITY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_VELOCITY);

        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_MINIMESSAGE);
        logger.info("Runtime libraries loaded successfully");
    }

    private void loadLibrary(VelocityLibraryManager<VelocityPlugin> libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.groupId())
                .artifactId(record.artifactId())
                .version(record.version())
                .id(record.id());

        if (record.hasRelocation()) builder.relocate(record.oldRelocation(), record.newRelocation());
        if (record.url() != null) builder.url(record.url());
        if (record.hasChecksum()) builder.checksum(record.checksum());

        libraryManager.loadLibrary(builder.build());
    }

    private void loadConfig() {
        try {
            if (!Files.exists(folder)) Files.createDirectories(folder);
            Path configFile = folder.resolve("config.yml");
            if (!Files.exists(configFile)) createDefaultConfig(configFile);

            try (InputStream inputStream = Files.newInputStream(configFile)) {
                this.configuration = yaml.load(inputStream);
                if (this.configuration == null) {
                    logger.warn("Configuration file is empty, using defaults");
                    this.configuration = Map.of();
                }
            }
            logger.info("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            this.configuration = Map.of();
        }
    }

    private void createDefaultConfig(Path configFile) throws IOException {
        try (InputStream defaultConfig = getClass().getResourceAsStream("/config.yml")) {
            if (defaultConfig != null) Files.copy(defaultConfig, configFile);
            else logger.warn("Default config resource not found in JAR");
        }
    }

    @SuppressWarnings("unchecked")
    private String getConfigString(String path, String defaultValue) {
        if (configuration == null) return defaultValue;
        String[] keys = path.split("\\.");
        Object current = configuration;
        for (String key : keys) {
            if (current instanceof Map) current = ((Map<String, Object>) current).get(key);
            else return defaultValue;
        }
        return current instanceof String ? (String) current : defaultValue;
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
