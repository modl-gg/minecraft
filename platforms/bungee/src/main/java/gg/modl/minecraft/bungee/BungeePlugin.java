package gg.modl.minecraft.bungee;

import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.bungee.CirrusBungee;
import gg.modl.minecraft.core.packet.PacketInterceptionDecision;
import gg.modl.minecraft.core.packet.PacketInterceptionMode;
import gg.modl.minecraft.core.packet.PacketInterceptionPolicy;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.core.AsyncCommandExecutor;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.BootConfigMigrator;
import gg.modl.minecraft.core.boot.LibraryLoader;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.boot.SyncPollingRate;
import gg.modl.minecraft.core.login.LoginPipeline;
import gg.modl.minecraft.core.login.ProxyLoginFlow;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.query.ProxyBridgeRuntime;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.config.yaml.CoreConfigBootstrap;
import io.github.retrooper.packetevents.bungee.factory.BungeePacketEventsBuilder;
import com.alessiodp.libby.BungeeLibraryManager;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import com.alessiodp.libby.logging.LogLevel;

public class BungeePlugin extends Plugin {
    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private Configuration configuration;
    private PluginLoader loader;
    private ProxyBridgeRuntime bridgeRuntime;
    private PluginLogger pluginLogger;
    private BootConfig bootConfig;
    private BungeeListener bungeeListener;

    @Override
    public synchronized void onEnable() {
        this.pluginLogger = PluginLogger.fromJul(getLogger());

        bootConfig = loadBootConfig();
        if (bootConfig == null) {
            getLogger().info("No configuration found. Starting setup wizard...");
            new BungeeSetupWizard(this, pluginLogger, this::initializeAfterWizard).start();
            return;
        }

        initializePlugin();
    }

    private synchronized void initializeAfterWizard(BootConfig config) {
        this.bootConfig = config;
        initializePlugin();
    }

    private void initializePlugin() {
        loadLibraries();
        createLocaleFiles();
        mergeDefaultConfigs();
        loadConfig();
        boolean packetInterception = initializePacketEvents();

        String panelUrl = StartupClient.callStartupWithRetry(
                bootConfig.getApiKey(), bootConfig.isTestingApi(),
                new StartupRequest(PluginInfo.VERSION, "BUNGEECORD",
                        getProxy().getVersion(), getProxy().getConfig().getPlayerLimit()),
                pluginLogger);
        if (panelUrl == null) {
            getLogger().severe("Failed to connect to modl.gg. Check your API key and network connection.");
            return;
        }

        HttpManager httpManager = new HttpManager(
                bootConfig.getApiKey(),
                panelUrl,
                configuration.getBoolean("debug", false),
                bootConfig.isTestingApi(),
                configuration.getBoolean("server.query_mojang", false)
        );

        if (packetInterception) new CirrusBungee(this).init();

        BungeePlatform platform = new BungeePlatform(this, getLogger(), getDataFolder(), configuration.getString("server.name", "Server 1"), packetInterception);
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = SyncPollingRate.clamp(configuration.getInt(SyncPollingRate.CONFIG_KEY, SyncPollingRate.DEFAULT_SECONDS));
        List<String> mutedCommands = configuration.getStringList("muted_commands");

        this.loader = new PluginLoader(platform, getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        configureBridgeExecutor(platform, bootConfig, panelUrl);

        CommandInterceptService commandInterceptService = new CommandInterceptService(
                loader.getCache(), loader.getFreezeService(), loader.getChatCommandLogService(),
                loader.getLocaleManager(), loader.getPunishmentMessageService(), mutedCommands);

        ProxyLoginFlow proxyLoginFlow = new ProxyLoginFlow(
                loader.getHttpClientHolder(), loader.getLoginCache(), loader.getLoginService(),
                loader.getIpEnrichmentService(),
                loader.getPendingIpLookupService(), LOGIN_TIMEOUT_SECONDS);

        LoginPipeline loginPipeline = new LoginPipeline(
                loader.getLoginService(), loader.getLoginCache(), loader.getPlayerSessionService());

        bungeeListener = new BungeeListener(
                platform, loader.getCache(), this, loginPipeline,
                loader.getChatService(), commandInterceptService,
                proxyLoginFlow, loader.getServerSwitchService());
        getProxy().getPluginManager().registerListener(this, bungeeListener);

        AsyncCommandExecutor asyncExecutor = loader.getAsyncCommandExecutor();
        getProxy().getPluginManager().registerListener(this, new AsyncCommandInterceptor(
                asyncExecutor, getProxy(), commandInterceptService));

        getLogger().info("Successfully booted modl.gg platform plugin!");
    }

    @Override
    public synchronized void onDisable() {
        if (bridgeRuntime != null) bridgeRuntime.shutdown();
        if (loader != null) loader.shutdown();
        terminatePacketEvents();
    }

    private BootConfig loadBootConfig() {
        try {
            if (BootConfig.exists(getDataFolder().toPath())) {
                BootConfig config = BootConfig.load(getDataFolder().toPath());
                if (config != null && config.isValid()) {
                    return config;
                }
            }

            Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                    getDataFolder().toPath(), PlatformType.BUNGEECORD, pluginLogger);
            if (migrated.isPresent()) {
                return migrated.get();
            }

            return null;
        } catch (IOException e) {
            getLogger().severe("Failed to load boot.yml: " + e.getMessage());
            return null;
        }
    }

    private void mergeDefaultConfigs() {
        CoreConfigBootstrap.updateBootConfig(getDataFolder().toPath(), pluginLogger);
        CoreConfigBootstrap.updateRuntimeConfigs(getDataFolder().toPath(), pluginLogger);
    }

    private void configureBridgeExecutor(BungeePlatform platform, BootConfig bootConfig, String panelUrl) {
        bridgeRuntime = ProxyBridgeRuntime.startIfProxy(platform, loader, bootConfig, pluginLogger, panelUrl);
    }

    private boolean initializePacketEvents() {
        PacketInterceptionDecision decision = PacketInterceptionPolicy.decide(
                PacketInterceptionMode.fromConfig(configuration == null
                        ? null : configuration.get(PacketInterceptionPolicy.CONFIG_KEY)),
                pluginId -> getProxy().getPluginManager().getPlugins().stream()
                        .anyMatch(installed -> installed.getDescription().getName().equalsIgnoreCase(pluginId)));

        if (!decision.isEnabled()) {
            getLogger().warning("modl packet interception is off: " + decision.getReason());
            return false;
        }

        try {
            PacketEvents.setAPI(BungeePacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();
            PacketEvents.getAPI().init();
            return true;
        } catch (Throwable failure) {
            getLogger().log(Level.WARNING, "modl packet interception could not start on this proxy; proxy menus "
                    + "and secure-chat enforcement are off. Moderation is unaffected.", failure);
            terminatePacketEvents();
            return false;
        }
    }

    private void terminatePacketEvents() {
        if (PacketEvents.getAPI() == null) return;
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable failure) {
            getLogger().fine("PacketEvents termination failed: " + failure.getMessage());
        } finally {
            PacketEvents.setAPI(null);
        }
    }

    private void loadLibraries() {
        BungeeLibraryManager libraryManager = new BungeeLibraryManager(this);
        libraryManager.setLogLevel(LogLevel.WARN);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://nexus.modl.gg/repository/maven-releases/");
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.PROTO_DEPS_RELOCATED) loadLibrary(libraryManager, record);
        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.LAMP_COMMON);
        loadLibrary(libraryManager, Libraries.LAMP_BRIGADIER);
        loadLibrary(libraryManager, Libraries.LAMP_BUNGEE);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
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
    }

    private void loadLibrary(BungeeLibraryManager libraryManager, LibraryRecord record) {
        libraryManager.loadLibrary(LibraryLoader.toLibrary(record));
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
