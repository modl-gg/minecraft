package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.spigot.CirrusSpigot;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.query.BridgeMessageDispatcher;
import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class SpigotPlugin extends JavaPlugin {
    private static final String PLACEHOLDER_API_URL = "https://yourserver.modl.gg";
    private static final String BRIDGE_PLUGIN_NAME = "modl-bridge";
    private static final String DEFAULT_BRIDGE_NAME = "bridge";
    private static final int DEFAULT_BRIDGE_PORT = 25590;
    private static final int MIN_SYNC_POLLING_RATE = 1;
    private static final int DEFAULT_SYNC_POLLING_RATE = 2;
    private static final int BSTATS_PLUGIN_ID = 29705;

    private PluginLoader loader;
    private QueryStatWipeExecutor queryStatWipeExecutor;
    private PluginLogger pluginLogger;

    @Override
    public synchronized void onEnable() {
        this.pluginLogger = PluginLogger.fromJul(getLogger());
        loadLibraries();
        initializePacketEvents();
        saveDefaultConfig();
        createLocaleFiles();
        mergeDefaultConfigs();

        String apiUrl = getConfig().getString("api.url");
        if (PLACEHOLDER_API_URL.equals(apiUrl)) {
            logConfigurationError();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        HttpManager httpManager = new HttpManager(
                getConfig().getString("api.key"),
                apiUrl,
                getConfig().getBoolean("api.debug", false),
                getConfig().getBoolean("api.testing-api", false),
                getConfig().getBoolean("server.query_mojang", false)
        );

        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        new CirrusSpigot(this).init();

        SpigotPlatform platform = new SpigotPlatform(commandManager, getLogger(), getDataFolder(), getConfig().getString("server.name", "Server 1"), this);
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = Math.max(MIN_SYNC_POLLING_RATE, getConfig().getInt("sync.polling_rate", DEFAULT_SYNC_POLLING_RATE));
        List<String> mutedCommands = getConfig().getStringList("muted_commands");

        this.loader = new PluginLoader(platform, new SpigotCommandRegister(commandManager), getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        configureStatWipeExecutor(platform, httpManager);

        getServer().getPluginManager().registerEvents(new SpigotListener(
                platform, loader.getCache(), loader.getHttpClientHolder(), loader.getChatMessageCache(),
                loader.getSyncService(), loader.getLocaleManager(), loader.getLoginCache(),
                loader.isDebugMode(), mutedCommands, loader.getStaffChatService(),
                loader.getChatManagementService(), loader.getMaintenanceService(),
                loader.getFreezeService(), loader.getNetworkChatInterceptService(),
                loader.getChatCommandLogService(), loader.getStaff2faService(),
                loader.getConfigManager().getStaffChatConfig(), loader.getVanishService(),
                loader.getStaffModeService(), loader.getBridgeService()), this);

        new Metrics(this, BSTATS_PLUGIN_ID);
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

    private void configureStatWipeExecutor(SpigotPlatform platform, HttpManager httpManager) {
        // prefer direct java invocation (same-server bridge), fall back to TCP
        if (getServer().getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME) != null) {
            loader.getSyncService().setStatWipeExecutor(new SpigotStatWipeExecutor(pluginLogger, httpManager.isDebugHttp()));
            return;
        }

        String bridgeHost = getConfig().getString("bridge.host", "");
        if (bridgeHost.isEmpty()) {
            getLogger().warning("modl-bridge plugin not found and bridge.host not configured — stat wipe commands will not execute");
            return;
        }

        int bridgePort = getConfig().getInt("bridge.port", DEFAULT_BRIDGE_PORT);
        String apiKey = getConfig().getString("api.key");
        queryStatWipeExecutor = new QueryStatWipeExecutor(pluginLogger, httpManager.isDebugHttp());
        queryStatWipeExecutor.addBridge(DEFAULT_BRIDGE_NAME, bridgeHost, bridgePort, apiKey);
        loader.getSyncService().setStatWipeExecutor(queryStatWipeExecutor);

        BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                platform, loader.getLocaleManager(), loader.getFreezeService(),
                loader.getStaffModeService(), loader.getVanishService(), pluginLogger);
        queryStatWipeExecutor.setBridgeMessageDispatcher(dispatcher);
        loader.getBridgeService().setExecutor(queryStatWipeExecutor);
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
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_BUKKIT);
        loadLibrary(libraryManager, Libraries.CIRRUS_SPIGOT);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_SPIGOT);
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

    private void loadLibrary(BukkitLibraryManager libraryManager, LibraryRecord record) {
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

    private void createLocaleFiles() {
        try {
            File localeDir = new File(getDataFolder(), "locale");
            if (!localeDir.exists()) localeDir.mkdirs();

            File enUsFile = new File(localeDir, "en_US.yml");
            if (enUsFile.exists()) return;

            try (InputStream defaultLocale = getResource("locale/en_US.yml")) {
                if (defaultLocale != null) Files.copy(defaultLocale, enUsFile.toPath());
                else getLogger().warning("Default locale resource not found in JAR");
            }
        } catch (IOException e) {
            getLogger().severe("Failed to create locale files: " + e.getMessage());
        }
    }
}
