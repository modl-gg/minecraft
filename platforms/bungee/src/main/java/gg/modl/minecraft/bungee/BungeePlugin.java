package gg.modl.minecraft.bungee;

import co.aikar.commands.BungeeCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.bungee.CirrusBungee;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.query.BridgeMessageDispatcher;
import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
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
import java.util.logging.Level;

@Getter
public class BungeePlugin extends Plugin {
    private static final String PLACEHOLDER_API_URL = "https://yourserver.modl.gg", DEFAULT_BRIDGE_NAME = "bridge";
    private static final int DEFAULT_BRIDGE_PORT = 25590, MIN_SYNC_POLLING_RATE = 1, DEFAULT_SYNC_POLLING_RATE = 2;

    private Configuration configuration;
    private PluginLoader loader;
    private QueryStatWipeExecutor queryStatWipeExecutor;
    private PluginLogger pluginLogger;

    @Override
    public synchronized void onEnable() {
        this.pluginLogger = PluginLogger.fromJul(getLogger());
        loadLibraries();
        initializePacketEvents();
        loadConfig();
        createLocaleFiles();
        mergeDefaultConfigs();

        String apiUrl = configuration.getString("api.url");
        if (PLACEHOLDER_API_URL.equals(apiUrl)) {
            logConfigurationError();
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
        int syncPollingRate = Math.max(MIN_SYNC_POLLING_RATE, configuration.getInt("sync.polling_rate", DEFAULT_SYNC_POLLING_RATE));
        List<String> mutedCommands = configuration.getStringList("muted_commands");

        this.loader = new PluginLoader(platform,getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        configureBridgeExecutor(platform, httpManager);

        getProxy().getPluginManager().registerListener(this, new BungeeListener(
                platform, loader.getCache(), loader.getHttpClientHolder(), loader.getChatMessageCache(),
                loader.getSyncService(), loader.getLocaleManager(),
                mutedCommands, this, loader.getStaffChatService(),
                loader.getChatManagementService(), loader.getMaintenanceService(),
                loader.getFreezeService(), loader.getNetworkChatInterceptService(),
                loader.getChatCommandLogService(), loader.getStaff2faService(),
                loader.getConfigManager().getStaffChatConfig(), loader.getLoginCache(),
                loader.getBridgeService(), loader.getPlayerProfileRegistry(),
                loader.isDebugMode()));

        AsyncCommandExecutor asyncExecutor = loader.getAsyncCommandExecutor();
        getProxy().getPluginManager().registerListener(this, new AsyncCommandInterceptor(asyncExecutor, getProxy()));
    }

    @Override
    public synchronized void onDisable() {
        if (queryStatWipeExecutor != null) queryStatWipeExecutor.shutdown();
        if (loader != null) loader.shutdown();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }

    private void mergeDefaultConfigs() {
        YamlMergeUtil.mergeWithDefaults("/config.yml",
                getDataFolder().toPath().resolve("config.yml"), pluginLogger);
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml",
                getDataFolder().toPath().resolve("locale/en_US.yml"), pluginLogger);
    }

    private void logConfigurationError() {
        getLogger().severe("===============================================");
        getLogger().severe("modl.gg CONFIGURATION ERROR");
        getLogger().severe("===============================================");
        getLogger().severe("You must configure your API URL in config.yml!");
        getLogger().severe("Please set 'api.url' to your actual modl.gg panel URL.");
        getLogger().severe("Example: https://yourserver.modl.gg");
        getLogger().severe("Plugin will now disable itself.");
        getLogger().severe("===============================================");
    }

    private void configureBridgeExecutor(BungeePlatform platform, HttpManager httpManager) {
        String bridgeHost = configuration.getString("bridge.host", "");
        if (bridgeHost.isEmpty()) return;

        int bridgePort = configuration.getInt("bridge.port", DEFAULT_BRIDGE_PORT);
        String apiKey = configuration.getString("api.key");
        queryStatWipeExecutor = new QueryStatWipeExecutor(pluginLogger, httpManager.isDebugHttp());
        queryStatWipeExecutor.addBridge(DEFAULT_BRIDGE_NAME, bridgeHost, bridgePort, apiKey);
        loader.getSyncService().setStatWipeExecutor(queryStatWipeExecutor);

        BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                platform, loader.getLocaleManager(), loader.getFreezeService(),
                loader.getStaffModeService(), loader.getVanishService(),
                loader.getHttpClient(), pluginLogger);
        queryStatWipeExecutor.setBridgeMessageDispatcher(dispatcher);
        loader.getBridgeService().setExecutor(queryStatWipeExecutor);
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

        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_BUNGEE);
        loadLibrary(libraryManager, Libraries.CIRRUS_BUNGEECORD);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_BUNGEE);
        loadLibrary(libraryManager, Libraries.EXAMINATION_API);
        loadLibrary(libraryManager, Libraries.EXAMINATION_STRING);
        loadLibrary(libraryManager, Libraries.ADVENTURE_KEY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_API);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_LEGACY);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_JSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_SERIALIZER_GSON);
        loadLibrary(libraryManager, Libraries.ADVENTURE_TEXT_MINIMESSAGE);
        getLogger().info("Runtime libraries loaded successfully");
    }

    private void loadLibrary(BungeeLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.getGroupId())
                .artifactId(record.getArtifactId())
                .version(record.getVersion())
                .id(record.getId())
                .isolatedLoad(false);

        if (record.hasRelocation()) builder.relocate(record.getOldRelocation(), record.getNewRelocation());
        if (record.getUrl() != null) builder.url(record.getUrl());
        if (record.hasChecksum()) builder.checksum(record.getChecksum());

        libraryManager.loadLibrary(builder.build());
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream defaultConfig = getResourceAsStream("config.yml")) {
                if (defaultConfig != null) Files.copy(defaultConfig, file.toPath());
                else getLogger().warning("Default config resource not found in JAR");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to create default config file", e);
            }
        }

        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", e);
        }
    }

    private void createLocaleFiles() {
        try {
            File localeDir = new File(getDataFolder(), "locale");
            if (!localeDir.exists()) localeDir.mkdirs();

            File enUsFile = new File(localeDir, "en_US.yml");
            if (enUsFile.exists()) return;

            try (InputStream defaultLocale = getResourceAsStream("locale/en_US.yml")) {
                if (defaultLocale != null) Files.copy(defaultLocale, enUsFile.toPath());
                else getLogger().warning("Default locale resource not found in JAR");
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create locale files: " + e.getMessage());
        }
    }
}
