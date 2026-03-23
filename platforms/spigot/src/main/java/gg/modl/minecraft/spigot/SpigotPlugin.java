package gg.modl.minecraft.spigot;

import co.aikar.commands.BukkitCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import dev.simplix.cirrus.spigot.CirrusSpigot;
import gg.modl.minecraft.api.LibraryRecord;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.Libraries;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.boot.*;
import gg.modl.minecraft.core.query.BridgeMessageDispatcher;
import gg.modl.minecraft.core.query.QueryStatWipeExecutor;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;

import static gg.modl.minecraft.core.util.Java8Collections.*;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import gg.modl.minecraft.spigot.bridge.BridgeComponent;
import gg.modl.minecraft.spigot.bridge.reporter.TicketCreator;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SpigotPlugin extends JavaPlugin {
    private static final int MIN_SYNC_POLLING_RATE = 1;
    private static final int DEFAULT_SYNC_POLLING_RATE = 2;

    private PluginLoader loader;
    private QueryStatWipeExecutor queryStatWipeExecutor;
    private BridgeComponent bridgeComponent;
    private PluginLogger pluginLogger;
    private BootConfig bootConfig;
    private boolean needsSetup = false;

    @Override
    public synchronized void onLoad() {
        this.pluginLogger = PluginLogger.fromJul(getLogger());

        bootConfig = loadBootConfig();
        if (bootConfig != null) {
            loadLibraries();

            bridgeComponent = new BridgeComponent(this, "", "", "", pluginLogger);
            bridgeComponent.onLoad();
        } else {
            needsSetup = true;
        }
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

        loadLibraries();

        bridgeComponent = new BridgeComponent(this, "", "", "", pluginLogger);

        initializePlugin();
    }

    private void initializePlugin() {
        ensureSignedVelocity();

        loadPacketEventsLibraries();
        loadPacketEvents();
        initPacketEvents();

        saveDefaultConfig();
        createLocaleFiles();
        mergeDefaultConfigs();

        String backendUrl = bootConfig.isTestingApi() ? HttpManager.TESTING_API_URL : HttpManager.V2_API_URL;
        String panelUrl = HttpManager.adjustPanelUrlForEnv(
                bootConfig.getPanelUrl() != null ? bootConfig.getPanelUrl() : "",
                bootConfig.isTestingApi());
        bridgeComponent = new BridgeComponent(this, bootConfig.getApiKey(), backendUrl, panelUrl, pluginLogger);

        BootConfig.Mode mode = bootConfig.getMode();
        if (mode == BootConfig.Mode.STANDALONE) {
            enableStandaloneMode(bootConfig);
        } else if (mode == BootConfig.Mode.BRIDGE_ONLY) {
            enableBridgeOnlyMode(bootConfig);
        } else if (mode == BootConfig.Mode.PROXY) {
            getLogger().severe("boot.yml mode is 'proxy' but this is a Spigot server. Use 'standalone' or 'bridge-only'.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void enableStandaloneMode(BootConfig bootConfig) {
        HttpManager httpManager = new HttpManager(
                bootConfig.getApiKey(),
                bootConfig.getPanelUrl(),
                getConfig().getBoolean("api.debug", false),
                bootConfig.isTestingApi(),
                getConfig().getBoolean("server.query_mojang", false)
        );

        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        new CirrusSpigot(this).init();

        TicketCreator ticketCreator = (creatorUuid, creatorName, type, subject, description,
                                       reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
            List<String> tags = tagsJoined == null || tagsJoined.isEmpty() ? listOf() : Arrays.asList(tagsJoined.split(","));
            CreateTicketRequest request = new CreateTicketRequest(
                    creatorUuid, type, creatorName, subject, description,
                    reportedPlayerUuid, reportedPlayerName, priority, createdServer,
                    null, tags, replayUrl
            );
            loader.getHttpClient().createTicket(request).thenAccept(response -> {
                if (response.isSuccess()) {
                    getLogger().info("[bridge] Report ticket created: " + response.getTicketId());
                } else {
                    getLogger().warning("[bridge] Failed to create report ticket: " + response.getMessage());
                }
            }).exceptionally(throwable -> {
                getLogger().warning("[bridge] Error creating report ticket: " + throwable.getMessage());
                return null;
            });
        };

        bridgeComponent.enable(ticketCreator);
        String serverName = bridgeComponent.getBridgeConfig().getServerName();

        SpigotPlatform platform = new SpigotPlatform(commandManager, getLogger(), getDataFolder(),
                serverName, this);
        if (bridgeComponent.getReplayService() != null) {
            platform.setReplayService(bridgeComponent.getReplayService());
        }
        ChatMessageCache chatMessageCache = new ChatMessageCache();
        int syncPollingRate = Math.max(MIN_SYNC_POLLING_RATE, getConfig().getInt("sync.polling_rate", DEFAULT_SYNC_POLLING_RATE));
        List<String> mutedCommands = getConfig().getStringList("muted_commands");

        this.loader = new PluginLoader(platform, getDataFolder().toPath(), chatMessageCache, httpManager, syncPollingRate);

        DirectStatWipeExecutor directExecutor = new DirectStatWipeExecutor(bridgeComponent, serverName);
        loader.getSyncService().setStatWipeExecutor(directExecutor);

        if (bridgeComponent.getQueryServer() != null) {
            queryStatWipeExecutor = new QueryStatWipeExecutor(pluginLogger, httpManager.isDebugHttp());
            BridgeMessageDispatcher dispatcher = new BridgeMessageDispatcher(
                    platform, loader.getLocaleManager(), loader.getFreezeService(),
                    loader.getStaffModeService(), loader.getVanishService(),
                    loader.getHttpClient(), pluginLogger);
            queryStatWipeExecutor.setBridgeMessageDispatcher(dispatcher);
            loader.getBridgeService().setExecutor(queryStatWipeExecutor);
        }

        // Standalone: dispatch bridge actions directly to local handlers
        // instead of routing through TCP
        loader.getBridgeService().setLocalHandler(new BridgeService.LocalBridgeHandler() {
            @Override public void onStaffModeEnter(String staffUuid) {
                bridgeComponent.getStaffModeHandler().enterStaffMode(staffUuid);
            }
            @Override public void onStaffModeExit(String staffUuid) {
                bridgeComponent.getStaffModeHandler().exitStaffMode(staffUuid);
            }
            @Override public void onVanishEnter(String staffUuid) {
                bridgeComponent.getStaffModeHandler().vanishFromBridge(staffUuid);
            }
            @Override public void onVanishExit(String staffUuid) {
                bridgeComponent.getStaffModeHandler().unvanishFromBridge(staffUuid);
            }
            @Override public void onFreezePlayer(String targetUuid, String staffUuid) {
                bridgeComponent.getFreezeHandler().freeze(targetUuid, staffUuid);
            }
            @Override public void onUnfreezePlayer(String targetUuid) {
                bridgeComponent.getFreezeHandler().unfreeze(targetUuid);
            }
            @Override public void onTargetRequest(String staffUuid, String targetUuid) {
                bridgeComponent.getStaffModeHandler().setTarget(staffUuid, targetUuid);
            }
        });

        getServer().getPluginManager().registerEvents(new SpigotListener(
                platform, loader.getCache(), loader.getHttpClientHolder(), loader.getChatMessageCache(),
                loader.getSyncService(), loader.getLocaleManager(), loader.getLoginCache(),
                mutedCommands, loader.getStaffChatService(),
                loader.getChatManagementService(), loader.getMaintenanceService(),
                loader.getFreezeService(), loader.getNetworkChatInterceptService(),
                loader.getChatCommandLogService(), loader.getStaff2faService(),
                loader.getConfigManager().getStaffChatConfig(),
                loader.getBridgeService(), loader.getCachedProfileRegistry(),
                loader.isDebugMode()), this);
    }

    private void enableBridgeOnlyMode(BootConfig bootConfig) {
        TicketCreator tcpTicketCreator = (creatorUuid, creatorName, type, subject, description,
                                          reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
            if (bridgeComponent.getQueryServer() != null) {
                if (replayUrl != null && !replayUrl.isEmpty()) {
                    bridgeComponent.getQueryServer().sendToAllClients("CREATE_REPORT",
                            creatorUuid, creatorName, type, subject, description,
                            reportedPlayerUuid, reportedPlayerName,
                            tagsJoined != null ? tagsJoined : "",
                            priority, createdServer, replayUrl);
                } else {
                    bridgeComponent.getQueryServer().sendToAllClients("CREATE_REPORT",
                            creatorUuid, creatorName, type, subject, description,
                            reportedPlayerUuid, reportedPlayerName,
                            tagsJoined != null ? tagsJoined : "",
                            priority, createdServer);
                }
            }
        };

        bridgeComponent.enable(tcpTicketCreator);
        getLogger().info("Running in bridge-only mode (no main plugin features)");
    }

    @Override
    public synchronized void onDisable() {
        if (bridgeComponent != null) bridgeComponent.disable();
        if (queryStatWipeExecutor != null) queryStatWipeExecutor.shutdown();
        if (loader != null) loader.shutdown();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }

    private void ensureSignedVelocity() {
        if (bootConfig.getMode() != BootConfig.Mode.BRIDGE_ONLY) return;
        if (!"velocity".equalsIgnoreCase(bootConfig.getProxyType())) return;
        if (getServer().getPluginManager().getPlugin("SignedVelocity") != null) return;

        Path pluginsFolder = getDataFolder().getParentFile().toPath();
        Path jar = SignedVelocityDownloader.ensureDownloaded(SignedVelocityDownloader.Platform.PAPER, pluginsFolder, pluginLogger);
        if (jar == null) return;

        try {
            Plugin sv = getServer().getPluginManager().loadPlugin(jar.toFile());
            if (sv != null) {
                getServer().getPluginManager().enablePlugin(sv);
                getLogger().info("[SignedVelocity] Loaded and enabled");
            }
        } catch (Exception e) {
            getLogger().warning("[SignedVelocity] Runtime load failed: " + e.getMessage());
            getLogger().warning("[SignedVelocity] It will be active after a server restart.");
        }
    }

    private BootConfig loadBootConfig() {
        try {
            if (BootConfig.exists(getDataFolder().toPath())) {
                BootConfig config = BootConfig.load(getDataFolder().toPath());
                if (config != null && config.isValid()) {
                    getLogger().info("Loaded configuration from boot.yml (mode: " + config.getMode().toYaml() + ")");
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

    private void mergeDefaultConfigs() {
        YamlMergeUtil.mergeWithDefaults("/boot.yml",
                getDataFolder().toPath().resolve("boot.yml"), pluginLogger);
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
        getLogger().info("PacketEvents initialized successfully");
    }

    private BukkitLibraryManager libraryManager;

    private void loadLibraries() {
        libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();
        libraryManager.addRepository("https://repo.codemc.io/repository/maven-releases/");
        libraryManager.addRepository("https://jitpack.io");

        for (LibraryRecord record : Libraries.COMMON) loadLibrary(libraryManager, record);
        loadLibrary(libraryManager, Libraries.ACF_CORE);
        loadLibrary(libraryManager, Libraries.ACF_BUKKIT);
        loadLibrary(libraryManager, Libraries.SLF4J_API);
        loadLibrary(libraryManager, Libraries.SLF4J_SIMPLE);
        loadLibrary(libraryManager, Libraries.CIRRUS_SPIGOT);
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

    private void loadPacketEventsLibraries() {
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_API);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_NETTY);
        loadLibrary(libraryManager, Libraries.PACKETEVENTS_SPIGOT);
    }

    private void loadLibrary(BukkitLibraryManager libraryManager, LibraryRecord record) {
        Library.Builder builder = Library.builder()
                .groupId(record.getGroupId())
                .artifactId(record.getArtifactId())
                .version(record.getVersion())
                .id(record.getId());

        if (record.hasRelocations()) {
            for (String[] relocation : record.getRelocations()) {
                builder.relocate(relocation[0], relocation[1]);
            }
        }
        if (record.getUrl() != null) builder.url(record.getUrl());
        if (record.hasChecksum()) builder.checksum(record.getChecksum());

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
