package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.bungee.CirrusBungee;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.YamlMergeUtil;
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
import java.util.List;
import org.bstats.bungeecord.Metrics;

@Getter
public class BungeePlugin extends Plugin {
    private Configuration configuration;
    private PluginLoader loader;
    private QueryStatWipeExecutor queryStatWipeExecutor;

    @Override
    public synchronized void onEnable() {
        // Load runtime dependencies via libby before anything else
        loadLibraries();

        // Initialize PacketEvents before Cirrus
        initializePacketEvents();

        loadConfig();
        createLocaleFiles();

        // Auto-merge new keys from plugin update
        YamlMergeUtil.mergeWithDefaults("/config.yml",
                getDataFolder().toPath().resolve("config.yml"), getLogger());
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml",
                getDataFolder().toPath().resolve("locale/en_US.yml"), getLogger());

        // Validate configuration before proceeding
        String apiUrl = configuration.getString("api.url");
        if ("https://yourserver.modl.gg".equals(apiUrl)) {
            getLogger().severe("===============================================");
            getLogger().severe("modl.gg CONFIGURATION ERROR");
            getLogger().severe("===============================================");
            getLogger().severe("You must configure your API URL in config.yml!");
            getLogger().severe("Please set 'api.url' to your actual modl.gg panel URL.");
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
                configuration.getBoolean("server.query_mojang", false)
        );

        BungeeCommandManager commandManager = new BungeeCommandManager(this);
        new CirrusBungee(this).init();

        BungeePlatform platform = new BungeePlatform(commandManager, getLogger(), getDataFolder(), configuration.getString("server.name", "Server 1"));
        ChatMessageCache chatMessageCache = new ChatMessageCache();

        // Get sync polling rate from config (default: 2 seconds, minimum: 1 second)
        int syncPollingRate = Math.max(1, configuration.getInt("sync.polling_rate", 2));

        List<String> mutedCommands = configuration.getStringList("muted_commands");

        this.loader = new PluginLoader(platform, new BungeeCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);

        // Set up bridge TCP connection for stat-wipe execution
        // Uses the API key as shared secret for bridge authentication
        String bridgeHost = configuration.getString("bridge.host", "");
        if (!bridgeHost.isEmpty()) {
            int bridgePort = configuration.getInt("bridge.port", 25590);
            String apiKey = configuration.getString("api.key");
            queryStatWipeExecutor = new QueryStatWipeExecutor(getLogger(), httpManager.isDebugHttp());
            queryStatWipeExecutor.addBridge("bridge", bridgeHost, bridgePort, apiKey);
            loader.getSyncService().setStatWipeExecutor(queryStatWipeExecutor);
        }
        getProxy().getPluginManager().registerListener(this, new BungeeListener(platform, loader.getCache(), loader.getHttpClientHolder(), chatMessageCache, loader.getSyncService(), loader.getLocaleManager(), httpManager.isDebugHttp(), mutedCommands, this));

        // Register async command interceptor — dispatches modl commands off the network thread
        // Uses a named class (not anonymous) to avoid BungeeCord EventBus reflection issues
        AsyncCommandExecutor asyncExecutor = loader.getAsyncCommandExecutor();
        getProxy().getPluginManager().registerListener(this, new AsyncCommandInterceptor(asyncExecutor, getProxy()));

        new Metrics(this, 29831);
    }

    @Override
    public synchronized void onDisable() {
        if (queryStatWipeExecutor != null) {
            queryStatWipeExecutor.shutdown();
        }
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

        if (record.hasChecksum()) {
            builder.checksum(record.checksum());
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
