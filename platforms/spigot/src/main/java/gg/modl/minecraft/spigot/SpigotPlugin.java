package gg.modl.minecraft.spigot;

import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.spigot.CirrusSpigot;
import gg.modl.minecraft.bridge.config.BridgeWizardConfigWriter;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.core.boot.BackendHost;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.BootConfigMigrator;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import gg.modl.minecraft.spigot.boot.LibraryBootstrap;
import gg.modl.minecraft.spigot.boot.SignedVelocityBootstrap;
import gg.modl.minecraft.spigot.boot.TicketCreatorFactory;
import gg.modl.minecraft.spigot.bridge.BridgeComponent;
import gg.modl.minecraft.spigot.bridge.SpigotStandaloneLocalBridgeHandler;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.loader.LoaderApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public class SpigotPlugin extends JavaPlugin {
    private static final int MIN_SYNC_POLLING_RATE = 1;
    private static final int DEFAULT_SYNC_POLLING_RATE = 2;

    private PluginLoader loader;
    private BridgeComponent bridgeComponent;
    private PluginLogger pluginLogger;
    private BootConfig bootConfig;
    private boolean needsSetup = false;
    private boolean polarLoaderAvailable = false;
    private volatile boolean polarEnabledBeforeBridgeInit = false;
    private LibraryBootstrap libraryBootstrap;
    private SignedVelocityBootstrap signedVelocityBootstrap;

    @Override
    public synchronized void onLoad() {
        this.pluginLogger = PluginLogger.fromJul(getLogger());
        this.libraryBootstrap = new LibraryBootstrap(this);
        this.signedVelocityBootstrap = new SignedVelocityBootstrap(this);
        registerPolarLoaderCallback();

        bootConfig = loadBootConfig();
        if (bootConfig != null) {
            libraryBootstrap.loadRuntimeLibraries();
        } else {
            needsSetup = true;
        }

        libraryBootstrap.loadPacketEventsLibraries();
        loadPacketEvents();
    }

    @Override
    public synchronized void onEnable() {
        if (needsSetup) {
            getLogger().info("No configuration found. Starting setup wizard...");
            new SpigotSetupWizard(this, pluginLogger, this::initializeAfterWizard).start();
            return;
        }

        initializePlugin();
    }

    private synchronized void initializeAfterWizard(BootConfig config) {
        this.bootConfig = config;
        this.needsSetup = false;

        Thread initThread = new Thread(() -> {
            libraryBootstrap.loadRuntimeLibraries();

            String panelUrl = "";
            BootConfig.Mode mode = bootConfig.getMode();
            if (mode == BootConfig.Mode.STANDALONE || mode == BootConfig.Mode.PROXY) {
                panelUrl = StartupClient.callStartupWithRetry(
                        bootConfig.getApiKey(), bootConfig.isTestingApi(),
                        new StartupRequest(PluginInfo.VERSION, "SPIGOT",
                                getServer().getVersion(), getServer().getMaxPlayers()),
                        pluginLogger);
                if (panelUrl == null) {
                    getLogger().severe("Failed to connect to modl.gg. Check your API key and network connection.");
                    Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
                    return;
                }
            }

            final String finalPanelUrl = panelUrl;
            Bukkit.getScheduler().runTask(this, () -> initializePluginWithPanelUrl(finalPanelUrl, true));
        }, "modl-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    private void initializePlugin() {
        BootConfig.Mode mode = bootConfig.getMode();

        String panelUrl = "";
        if (mode == BootConfig.Mode.STANDALONE || mode == BootConfig.Mode.PROXY) {
            panelUrl = StartupClient.callStartupWithRetry(
                    bootConfig.getApiKey(), bootConfig.isTestingApi(),
                    new StartupRequest(PluginInfo.VERSION, "SPIGOT",
                            getServer().getVersion(), getServer().getMaxPlayers()),
                    pluginLogger);
            if (panelUrl == null) {
                getLogger().severe("Failed to connect to modl.gg. Check your API key and network connection.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        initializePluginWithPanelUrl(panelUrl, false);
    }

    private void initializePluginWithPanelUrl(String panelUrl, boolean lateBootstrap) {
        initPacketEvents();

        BootConfig.Mode mode = bootConfig.getMode();

        String backendUrl = BackendHost.resolve(bootConfig.isTestingApi());
        bridgeComponent = new BridgeComponent(this, bootConfig.getApiKey(), backendUrl, panelUrl,
                pluginLogger, polarLoaderAvailable);

        signedVelocityBootstrap.init(bootConfig);

        if (mode == BootConfig.Mode.STANDALONE) {
            saveDefaultConfig();
            createLocaleFiles();
            mergeDefaultConfigs();
            enableStandaloneMode(bootConfig, panelUrl, lateBootstrap);
        } else if (mode == BootConfig.Mode.BRIDGE_ONLY) {
            mergeBootConfig();
            enableBridgeOnlyMode(bootConfig);
        } else if (mode == BootConfig.Mode.PROXY) {
            getLogger().severe("boot.yml mode is 'proxy' but this is a Spigot server. Use 'standalone' or 'bridge-only'.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (polarEnabledBeforeBridgeInit) {
            polarEnabledBeforeBridgeInit = false;
            bridgeComponent.hookPolar();
        }

        getLogger().info("Successfully booted modl.gg platform plugin!");
    }

    private void enableStandaloneMode(BootConfig bootConfig, String panelUrl, boolean lateBootstrap) {
        HttpManager httpManager = new HttpManager(
                bootConfig.getApiKey(),
                panelUrl,
                getConfig().getBoolean("debug", false),
                bootConfig.isTestingApi(),
                getConfig().getBoolean("server.query_mojang", false)
        );

        new CirrusSpigot(this).init();

        TicketCreator ticketCreator = TicketCreatorFactory.standalone(this, () -> loader.getHttpClient());

        bridgeComponent.enable(ticketCreator, false);
        String serverName = bridgeComponent.getBridgeConfig().getServerName();

        SpigotPlatform platform = new SpigotPlatform(this, getLogger(), getDataFolder(), serverName, lateBootstrap);
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = Math.max(MIN_SYNC_POLLING_RATE, getConfig().getInt("sync.polling_rate", DEFAULT_SYNC_POLLING_RATE));
        List<String> mutedCommands = getConfig().getStringList("muted_commands");

        this.loader = new PluginLoader(platform, getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);
        if (bridgeComponent.getReplayService() != null) {
            PluginServices.get().setReplayService(bridgeComponent.getReplayService());
        }

        DirectStatWipeExecutor directExecutor = new DirectStatWipeExecutor(bridgeComponent, serverName);
        loader.getSyncService().setStatWipeExecutor(directExecutor);

        loader.getBridgeService().setLocalHandler(new SpigotStandaloneLocalBridgeHandler(
                bridgeComponent.getStaffModeHandler(),
                bridgeComponent.getFreezeHandler()));

        CommandInterceptService commandInterceptService = new CommandInterceptService(
                loader.getCache(), loader.getFreezeService(), loader.getChatCommandLogService(),
                loader.getLocaleManager(), loader.getPunishmentMessageService(), mutedCommands);

        getServer().getPluginManager().registerEvents(new SpigotListener(
                platform, loader.getCache(), loader.getHttpClientHolder(), loader.getChatMessageCache(),
                loader.getLocaleManager(), loader.getLoginCache(),
                loader.getChatService(), commandInterceptService,
                loader.getLoginService(), loader.getPlayerSessionService(),
                loader.getIpEnrichmentService(), loader.getPendingIpLookupService()), this);
    }

    private void enableBridgeOnlyMode(BootConfig bootConfig) {
        BridgeWizardConfigWriter.writeBridgeOnlyConfig(getDataFolder().toPath(), bootConfig, pluginLogger);

        TicketCreator tcpTicketCreator = TicketCreatorFactory.bridgeOnly(bridgeComponent);

        bridgeComponent.enable(tcpTicketCreator, true);
    }

    @Override
    public synchronized void onDisable() {
        signedVelocityBootstrap.shutdown();
        if (bridgeComponent != null) bridgeComponent.disable();
        if (loader != null) loader.shutdown();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }

    private void registerPolarLoaderCallback() {
        try {
            Class.forName("top.polar.api.loader.LoaderApi");
            polarLoaderAvailable = true;
            LoaderApi.registerEnableCallback(this::onPolarEnabled);
        } catch (ClassNotFoundException ignored) {}
    }

    private void onPolarEnabled() {
        BridgeComponent component = bridgeComponent;
        if (isEnabled() && component != null) {
            component.hookPolar();
        } else {
            polarEnabledBeforeBridgeInit = true;
        }
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
                    getDataFolder().toPath(), PlatformType.SPIGOT, pluginLogger);
            if (migrated.isPresent()) {
                return migrated.get();
            }

            return null;
        } catch (IOException e) {
            getLogger().severe("Failed to load boot.yml: " + e.getMessage());
            return null;
        }
    }

    private void mergeBootConfig() {
        YamlMergeUtil.mergeWithDefaults("/boot.yml",
                getDataFolder().toPath().resolve("boot.yml"), pluginLogger);
    }

    private void mergeDefaultConfigs() {
        mergeBootConfig();
        YamlMergeUtil.mergeWithDefaults("/config.yml",
                getDataFolder().toPath().resolve("config.yml"), pluginLogger);
        YamlMergeUtil.mergeWithDefaults("/locale/en_US.yml",
                getDataFolder().toPath().resolve("locale/en_US.yml"), pluginLogger);
    }

    private void loadPacketEvents() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    private void initPacketEvents() {
        PacketEvents.getAPI().init();
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
