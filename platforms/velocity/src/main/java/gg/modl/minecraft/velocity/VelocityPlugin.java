package gg.modl.minecraft.velocity;

import co.aikar.commands.VelocityCommandManager;
import com.google.inject.Inject;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.ChatMessageCache;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.simplix.cirrus.velocity.CirrusVelocity;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private Map<String, Object> configuration;
    private PluginLoader pluginLoader;

    @Inject
    public VelocityPlugin(PluginContainer plugin, ProxyServer server, @DataDirectory Path folder, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.folder = folder;
        this.logger = logger;
    }

    @Subscribe
    public synchronized void onProxyInitialize(ProxyInitializeEvent evt) {
        loadConfig();
        createLocaleFiles();

        // Load configuration with defaults
        loadConfig();
        
        // Validate configuration before proceeding
        String apiUrl = getConfigString("api.url", "https://yourserver.modl.gg");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            logger.error("===============================================");
            logger.error("MODL CONFIGURATION ERROR");
            logger.error("===============================================");
            logger.error("You must configure your API URL in config.yml!");
            logger.error("Please set 'api.url' to your actual MODL panel URL.");
            logger.error("Example: https://yourserver.modl.gg");
            logger.error("Plugin initialization stopped due to invalid configuration.");
            logger.error("===============================================");;
            return;
        }
        
        VelocityCommandManager commandManager = new VelocityCommandManager(this.server, this);
        new CirrusVelocity(this, server, server.getCommandManager()).init();

        HttpManager httpManager = new HttpManager(
                getConfigString("api.key", "your-api-key-here"),
                apiUrl,
                (Boolean) getNestedConfig("api.debug", false),
                (Boolean) getNestedConfig("api.testing-api", false),
                getConfigString("api.force-version", "auto")
        );

        VelocityPlatform platform = new VelocityPlatform(this.server, commandManager, logger, folder.toFile());
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        
        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, getConfigInt("sync.polling_rate", 2));
        
        this.pluginLoader = new PluginLoader(platform, new VelocityCommandRegister(commandManager), folder, chatMessageCache, httpManager, syncPollingRate);

        server.getEventManager().register(this, new JoinListener(pluginLoader.getHttpClientHolder(), pluginLoader.getCache(), logger, chatMessageCache, platform, pluginLoader.getSyncService(), httpManager.getPanelUrl(), pluginLoader.getLocaleManager()));

        server.getEventManager().register(this, new ChatListener(platform, pluginLoader.getCache(), chatMessageCache, pluginLoader.getLocaleManager()));
    }

    @Subscribe
    public synchronized void onProxyShutdown(ProxyShutdownEvent evt) {
        if (pluginLoader != null) {
            pluginLoader.shutdown();
        }
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
        String defaultConfig = """
                # MODL Minecraft Plugin Configuration
                # 
                # API Configuration - Get these values from your MODL panel
                api:
                  # Your API key from the panel
                  key: "your-api-key-here"
                  # Your panel's base URL (e.g., https://yourserver.modl.gg)
                  url: "https://yourserver.modl.gg"
                  # Enable debug HTTP logging (default: false)
                  debug: false
                  # Use testing API (api.cobl.gg) instead of production API
                  # Only enable this for development/testing purposes
                  testing-api: false

                # Server Configuration
                server:
                  # Name of this server (used for identification in the panel)
                  name: "Server 1"
                  # Allow querying Mojang API for unknown players (default: false)
                  query_mojang: false
                  
                # Sync Configuration
                sync:
                  # How often to sync with the panel (in seconds)
                  # Default: 2 seconds. Minimum: 1 second
                  polling_rate: 2
                """;
        
        Files.writeString(configFile, defaultConfig);
        logger.info("Created default configuration file at: " + configFile);
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
