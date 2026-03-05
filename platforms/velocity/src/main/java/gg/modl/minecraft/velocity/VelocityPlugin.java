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

    private final PluginContainer plugin;
    private final ProxyServer server;
    private final Path folder;
    private final Logger logger;
    private final Metrics.Factory metrics;

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
    }

    @Subscribe
    public synchronized void onProxyInitialize(ProxyInitializeEvent evt) {
        // Load runtime dependencies via libby before anything else
        loadLibraries();

        // Initialize PacketEvents before Cirrus
        initializePacketEvents();

        loadConfig();
        createLocaleFiles();

        // Auto-merge new keys from plugin update
        YamlMergeUtil.mergeWithDefaults("/config.yml",
                folder.resolve("config.yml"), java.util.logging.Logger.getLogger("modl"));
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml",
                folder.resolve("locale/en_US.yml"), java.util.logging.Logger.getLogger("modl"));

        // Validate configuration before proceeding
        String apiUrl = getConfigString("api.url", "https://yourserver.modl.gg");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            logger.error("===============================================");
            logger.error("modl.gg CONFIGURATION ERROR");
            logger.error("===============================================");
            logger.error("You must configure your API URL in config.yml!");
            logger.error("Please set 'api.url' to your actual modl.gg panel URL.");
            logger.error("Example: https://yourserver.modl.gg");
            logger.error("Plugin initialization stopped due to invalid configuration.");
            logger.error("===============================================");;
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
        
        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, getConfigInt("sync.polling_rate", 2));
        
        this.pluginLoader = new PluginLoader(platform, new VelocityCommandRegister(commandManager), folder, chatMessageCache, httpManager, syncPollingRate);

        // Set up bridge TCP connection for stat-wipe execution
        // Uses the API key as shared secret for bridge authentication
        String bridgeHost = getConfigString("bridge.host", "");
        if (!bridgeHost.isEmpty()) {
            int bridgePort = getConfigInt("bridge.port", 25590);
            String apiKey = getConfigString("api.key", "your-api-key-here");
            queryStatWipeExecutor = new QueryStatWipeExecutor(
                    java.util.logging.Logger.getLogger("modl"), httpManager.isDebugHttp());
            queryStatWipeExecutor.addBridge("bridge", bridgeHost, bridgePort, apiKey);
            pluginLoader.getSyncService().setStatWipeExecutor(queryStatWipeExecutor);

            // Wire up bridge message dispatcher for incoming messages
            BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                    platform, pluginLoader.getLocaleManager(), pluginLoader.getFreezeService(),
                    pluginLoader.getStaffModeService(), pluginLoader.getVanishService(),
                    java.util.logging.Logger.getLogger("modl"));
            queryStatWipeExecutor.setBridgeMessageDispatcher(dispatcher);

            // Set executor on bridge service for outgoing messages
            pluginLoader.getBridgeService().setExecutor(queryStatWipeExecutor);
        }

        server.getEventManager().register(this, new JoinListener(pluginLoader.getHttpClientHolder(), pluginLoader.getCache(), logger, chatMessageCache, platform, pluginLoader.getSyncService(), pluginLoader.getLocaleManager(), httpManager.isDebugHttp(), pluginLoader.getStaffChatService(), pluginLoader.getChatManagementService(), pluginLoader.getMaintenanceService(), pluginLoader.getFreezeService(), pluginLoader.getNetworkChatInterceptService(), pluginLoader.getStaff2faService()));

        @SuppressWarnings("unchecked")
        List<String> mutedCommands = (List<String>) getNestedConfig("muted_commands", Collections.emptyList());

        server.getEventManager().register(this, new ChatListener(platform, pluginLoader.getCache(), chatMessageCache, pluginLoader.getLocaleManager(), mutedCommands, pluginLoader.getStaffChatService(), pluginLoader.getChatManagementService(), pluginLoader.getFreezeService(), pluginLoader.getNetworkChatInterceptService(), pluginLoader.getChatCommandLogService(), pluginLoader.getConfigManager().getStaffChatConfig()));

        metrics.make(this, 29830);
    }

    @Subscribe
    public synchronized void onProxyShutdown(ProxyShutdownEvent evt) {
        if (queryStatWipeExecutor != null) {
            queryStatWipeExecutor.shutdown();
        }
        if (pluginLoader != null) {
            pluginLoader.shutdown();
        }
        // Terminate PacketEvents
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
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

        // Load common libraries
        for (LibraryRecord record : Libraries.COMMON) {
            loadLibrary(libraryManager, record);
        }

        // Load ACF (core first, then platform-specific)
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_VELOCITY);

        // Load Cirrus (platform-specific shadow jar includes cirrus-api + cirrus-common)
        loadLibrary(libraryManager, Libraries.CIRRUS_VELOCITY);

        // Load PacketEvents (API first, then netty, then platform implementation)
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_VELOCITY);

        // Load Adventure serializers (Velocity bundles adventure-api but not these)
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

        if (record.hasRelocation()) {
            builder.relocate(record.oldRelocation(), record.newRelocation());
        }

        if (record.url() != null) {
            builder.url(record.url());
        }

        if (record.hasChecksum()) {
            builder.checksum(record.checksum());
        }

        libraryManager.loadLibrary(builder.build());
    }

    private void loadConfig() {
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }

            Path configFile = folder.resolve("config.yml");
            
            // Create default config if it doesn't exist
            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }
            
            // Load configuration
            try (InputStream inputStream = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                this.configuration = yaml.load(inputStream);
                
                if (this.configuration == null) {
                    logger.warn("Configuration file is empty, using defaults");
                    this.configuration = Map.of();
                }
            }
            
            logger.info("Configuration loaded successfully");
            
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            this.configuration = Map.of(); // Use empty config as fallback
        }
    }
    
    private void createDefaultConfig(Path configFile) throws IOException {
        try (InputStream defaultConfig = getClass().getResourceAsStream("/config.yml")) {
            if (defaultConfig != null) {
                Files.copy(defaultConfig, configFile);
                logger.info("Created default configuration file at: " + configFile);
            } else {
                logger.warn("Default config resource not found in JAR");
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private String getConfigString(String path, String defaultValue) {
        if (configuration == null) {
            return defaultValue;
        }
        
        String[] keys = path.split("\\.");
        Object current = configuration;
        
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return defaultValue;
            }
        }
        
        return current instanceof String ? (String) current : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    private Object getNestedConfig(String path, Object defaultValue) {
        if (configuration == null) {
            return defaultValue;
        }
        
        String[] keys = path.split("\\.");
        Object current = configuration;
        
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return defaultValue;
            }
        }
        
        return current != null ? current : defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    private int getConfigInt(String path, int defaultValue) {
        Object value = getNestedConfig(path, defaultValue);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return defaultValue;
        }
    }
    
    private void createLocaleFiles() {
        try {
            // Create locale directory
            Path localeDir = folder.resolve("locale");
            if (!Files.exists(localeDir)) {
                Files.createDirectories(localeDir);
                logger.info("Created locale directory at: " + localeDir);
            }
            
            // Create default en_US.yml if it doesn't exist
            Path enUsFile = localeDir.resolve("en_US.yml");
            if (!Files.exists(enUsFile)) {
                // Copy the default locale from resources
                try (InputStream defaultLocale = getClass().getResourceAsStream("/locale/en_US.yml")) {
                    if (defaultLocale != null) {
                        Files.copy(defaultLocale, enUsFile);
                        logger.info("Created default locale file at: " + enUsFile);
                    } else {
                        logger.warn("Default locale resource not found in JAR");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create locale files", e);
        }
    }

}
