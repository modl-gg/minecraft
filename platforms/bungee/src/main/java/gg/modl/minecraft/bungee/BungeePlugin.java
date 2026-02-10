package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.bungee.CirrusBungee;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.service.ChatMessageCache;
import io.github.retrooper.packetevents.bungee.factory.BungeePacketEventsBuilder;
import lombok.Getter;
import net.byteflux.libby.BungeeLibraryManager;
import net.byteflux.libby.Library;
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
        // Load runtime dependencies via libby before anything else
        loadLibraries();

        // Initialize PacketEvents before Cirrus
        initializePacketEvents();

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

        BungeePlatform platform = new BungeePlatform(commandManager, getLogger(), getDataFolder(), configuration.getString("server.name", "Server 1"));
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, configuration.getInt("sync.polling_rate", 2));

        this.loader = new PluginLoader(platform, new BungeeCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        getProxy().getPluginManager().registerListener(this, new BungeeListener(platform, loader.getCache(), loader.getHttpClientHolder(), chatMessageCache, loader.getSyncService(), loader.getLocaleManager(), httpManager.isDebugHttp()));
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
        PacketEvents.setAPI(BungeePacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().init();
        getLogger().info("PacketEvents initialized successfully");
    }

    private void loadLibraries() {
        BungeeLibraryManager libraryManager = new BungeeLibraryManager(this);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        // Load common libraries
        for (LibraryRecord record : Libraries.COMMON) {
            loadLibrary(libraryManager, record);
        }

        // Load ACF (core first, then platform-specific)
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_BUNGEE);

        // Load Cirrus (platform-specific shadow jar includes cirrus-api + cirrus-common)
        loadLibrary(libraryManager, Libraries.CIRRUS_BUNGEECORD);

        // Load PacketEvents (API first, then netty, then platform implementation)
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_BUNGEE);

        // Load Adventure (transitive deps first, then API, then serializers)
        loadLibrary(libraryManager, Libraries.EXAMINATION_API);
        loadLibrary(libraryManager, Libraries.EXAMINATION_STRING);
        loadLibrary(libraryManager, Libraries.ADVENTURE_KEY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_API);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);

        getLogger().info("Runtime libraries loaded successfully");
    }

    private void loadLibrary(BungeeLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.groupId())
                .artifactId(record.artifactId())
                .version(record.version())
                .id(record.id())
                .isolatedLoad(false);

        if (record.hasRelocation()) {
            builder.relocate(record.oldRelocation(), record.newRelocation());
        }

        if (record.url() != null) {
            builder.url(record.url());
        }

        libraryManager.loadLibrary(builder.build());
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream defaultConfig = getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, file.toPath());
                } else {
                    getLogger().warning("Default config resource not found in JAR");
                }
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
