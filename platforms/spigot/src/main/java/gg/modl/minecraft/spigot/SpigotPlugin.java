package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.spigot.CirrusSpigot;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.service.ChatMessageCache;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class SpigotPlugin extends JavaPlugin {
    
    private PluginLoader loader;

    @Override
    public synchronized void onEnable() {
        // Load runtime dependencies via libby before anything else
        loadLibraries();

        // Initialize PacketEvents before Cirrus
        initializePacketEvents();

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
            getLogger().severe("Example: https://yourserver.modl.gg");
            getLogger().severe("Plugin will now disable itself.");
            getLogger().severe("===============================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HttpManager httpManager = new HttpManager(
                getConfig().getString("api.key"),
                apiUrl,
                getConfig().getBoolean("api.debug", false),
                getConfig().getBoolean("api.testing-api", false),
                getConfig().getString("api.force-version", "auto")
        );

        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        new CirrusSpigot(this).init();

        SpigotPlatform platform = new SpigotPlatform(commandManager, getLogger(), getDataFolder());
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, getConfig().getInt("sync.polling_rate", 2));

        this.loader = new PluginLoader(platform, new SpigotCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        getServer().getPluginManager().registerEvents(new SpigotListener(platform, loader.getCache(), loader.getHttpClientHolder(), chatMessageCache, loader.getSyncService(), httpManager.getPanelUrl(), loader.getLocaleManager(), loader.getLoginCache(), httpManager.isDebugHttp()), this);

    }

    @Override
    public synchronized void onDisable() {
        if (loader != null) {
            loader.shutdown();
        }
        // Terminate PacketEvents
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
    }

    private void initializePacketEvents() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().init();
        getLogger().info("PacketEvents initialized successfully");
    }

    private void loadLibraries() {
        BukkitLibraryManager libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");

        for (LibraryRecord record : Libraries.COMMON) {
            loadLibrary(libraryManager, record);
        }

        // Load PacketEvents (API first, then netty, then platform implementation)
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_SPIGOT);

        getLogger().info("Runtime libraries loaded successfully");
    }

    private void loadLibrary(BukkitLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.groupId())
                .artifactId(record.artifactId())
                .version(record.version())
                .id(record.id());

        if (record.hasRelocation()) {
            builder.relocate(record.oldRelocation(), record.newRelocation());
        }

        libraryManager.loadLibrary(builder.build());
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