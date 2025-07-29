package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.service.ChatMessageCache;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class SpigotPlugin extends JavaPlugin {
    
    private PluginLoader loader;

    @Override
    public synchronized void onEnable() {
        saveDefaultConfig();
        createLocaleFiles();
        
        // Validate configuration before proceeding
        String apiUrl = getConfig().getString("api.url");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            getLogger().severe("===============================================");
            getLogger().severe("MODL CONFIGURATION ERROR");
            getLogger().severe("===============================================");
            getLogger().severe("You must configure your API URL in config.yml!");
            getLogger().severe("Please set 'api.url' to your actual MODL panel URL.");
            getLogger().severe("Example: https://yourcompany.modl.gg");
            getLogger().severe("Plugin will now disable itself.");
            getLogger().severe("===============================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        HttpManager httpManager = new HttpManager(
                getConfig().getString("api.key"),
                apiUrl,
                getConfig().getBoolean("api.debug", false)
        );

        BukkitCommandManager commandManager = new BukkitCommandManager(this);

        SpigotPlatform platform = new SpigotPlatform(commandManager, getLogger());
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        this.loader = new PluginLoader(platform, new SpigotCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager);
        getServer().getPluginManager().registerEvents(new SpigotListener(platform, loader.getCache(), loader.getHttpClient(), chatMessageCache, loader.getSyncService(), httpManager.getPanelUrl()), this);

    }

    @Override
    public synchronized void onDisable() {
        if (loader != null) {
            loader.shutdown();
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
                try (InputStream defaultLocale = getResource("locale/en_US.yml")) {
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