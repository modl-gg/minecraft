package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import dev.simplix.cirrus.bungee.CirrusBungee;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.Getter;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@Getter
public class BungeePlugin extends Plugin {
    private Configuration configuration;
    private PluginLoader loader;

    @Override
    public synchronized void onEnable() {
        loadConfig();
        createLocaleFiles();
        
        // Validate configuration before proceeding
        String apiUrl = configuration.getString("api.url");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            getLogger().severe("===============================================");
            getLogger().severe("MODL CONFIGURATION ERROR");
            getLogger().severe("===============================================");
            getLogger().severe("You must configure your API URL in config.yml!");
            getLogger().severe("Please set 'api.url' to your actual MODL panel URL.");
            getLogger().severe("Example: https://yourserver.modl.gg");
            getLogger().severe("Plugin will now disable itself.");
            getLogger().severe("===============================================");
            return;
        }

        HttpManager httpManager = new HttpManager(
                configuration.getString("api.key"),
                apiUrl,
                configuration.getBoolean("api.debug", false),
                configuration.getBoolean("api.testing-api", false),
                configuration.getString("api.force-version", "auto")
        );

        BungeeCommandManager commandManager = new BungeeCommandManager(this);
        new CirrusBungee(this).init();

        BungeePlatform platform = new BungeePlatform(commandManager, getLogger(), getDataFolder());
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, configuration.getInt("sync.polling_rate", 2));

        this.loader = new PluginLoader(platform, new BungeeCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        getProxy().getPluginManager().registerListener(this, new BungeeListener(platform, loader.getCache(), loader.getHttpClientHolder(), chatMessageCache, loader.getSyncService(), httpManager.getPanelUrl(), loader.getLocaleManager()));
    }

    @Override
    public synchronized void onDisable() {
        if (loader != null) {
            loader.shutdown();
        }
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
                // Create default config content
                Configuration defaultConfig = new Configuration();
                defaultConfig.set("api.key", "your-api-key-here");
                defaultConfig.set("api.url", "https://yourserver.modl.gg");
                defaultConfig.set("api.debug", false);
                defaultConfig.set("api.testing-api", false);
                defaultConfig.set("api.force-version", "auto");
                defaultConfig.set("server.name", "Server 1");
                defaultConfig.set("server.query_mojang", false);
                defaultConfig.set("sync.polling_rate", 2);
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(defaultConfig, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    private void createLocaleFiles() {
        try {
            // Create locale directory
            File localeDir = new File(getDataFolder(), "locale");
            if (!localeDir.exists()) {
                localeDir.mkdirs();
                getLogger().info("Created locale directory at: " + localeDir.getPath());
            }
            
            // Create default en_US.yml if it doesn't exist
            File enUsFile = new File(localeDir, "en_US.yml");
            if (!enUsFile.exists()) {
                // Copy the default locale from resources
                try (InputStream defaultLocale = getResourceAsStream("locale/en_US.yml")) {
                    if (defaultLocale != null) {
                        Files.copy(defaultLocale, enUsFile.toPath());
                        getLogger().info("Created default locale file at: " + enUsFile.getPath());
                    } else {
                        getLogger().warning("Default locale resource not found in JAR");
                    }
                }
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create locale files: " + e.getMessage());
        }
    }
}
