package gg.modl.minecraft.fabric.v26;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.simplix.cirrus.fabric.CirrusFabric;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.bridge.config.BridgeWizardConfigWriter;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.BootConfigMigrator;
import gg.modl.minecraft.core.boot.ConsoleInput;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.SetupWizard;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.github.retrooper.packetevents.PacketEvents;
import org.yaml.snakeyaml.Yaml;
import revxrsal.commands.Lamp;

public class ModlFabricModImpl implements DedicatedServerModInitializer {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final PluginLogger PLUGIN_LOGGER = new PluginLogger() {
        @Override
        public void info(String msg) {
            LOGGER.info(msg);
        }

        @Override
        public void warning(String msg) {
            LOGGER.warn(msg);
        }

        @Override
        public void severe(String msg) {
            LOGGER.error(msg);
        }
    };

    private FabricBridgeComponent bridgeComponent;
    private CirrusFabric cirrus;
    private BootConfig bootConfig;
    private PluginLoader pluginLoader;
    private FabricListener fabricListener;

    private TicketCreator standaloneTicketCreator;
    private boolean standaloneDebugMode;
    private List<String> standaloneMutedCommands = List.of();
    private FabricPlatform standaloneFabricPlatform;

    @Override
    public void onInitializeServer() {
        Path dataFolder = FabricLoader.getInstance().getConfigDir().resolve("modl");
        dataFolder.toFile().mkdirs();
        bootConfig = loadOrCreateBootConfig(dataFolder);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }

    private BootConfig loadOrCreateBootConfig(Path dataFolder) {
        try {
            if (BootConfig.exists(dataFolder)) {
                BootConfig config = BootConfig.load(dataFolder);
                if (config != null && config.isValid()) {
                    return config;
                }
            }

            Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                    dataFolder, PlatformType.FABRIC, PLUGIN_LOGGER);
            if (migrated.isPresent()) {
                return migrated.get();
            }

            LOGGER.info("No configuration found. Starting setup wizard...");
            ConsoleInput input = ConsoleInput.system(PLUGIN_LOGGER);
            SetupWizard wizard = new SetupWizard(PLUGIN_LOGGER, input, PlatformType.FABRIC);
            BootConfig config = wizard.run();

            if (config != null) {
                config.save(dataFolder);
                BridgeWizardConfigWriter.writeBridgeOnlyConfig(dataFolder, config, PLUGIN_LOGGER);
                return config;
            }

            logConfigurationError();
            return null;
        } catch (IOException e) {
            LOGGER.error("Failed to load boot.yml: {}", e.getMessage());
            return null;
        }
    }

    private PacketEventsAPI<?> requirePacketEventsApi() {
        PacketEventsAPI<?> packetEventsApi =
                PacketEvents.getAPI();
        if (packetEventsApi == null) {
            throw new IllegalStateException("PacketEvents Fabric mod did not initialize before modl startup");
        }
        return packetEventsApi;
    }

    private void onServerStarted(MinecraftServer server) {
        if (bootConfig == null) {
            return;
        }

        Path dataFolder = FabricLoader.getInstance().getConfigDir().resolve("modl");
        FabricBridgePluginContext context = new FabricBridgePluginContext(server, dataFolder);

        requirePacketEventsApi();

        try {
            cirrus = new CirrusFabric(server);
            cirrus.init();
        } catch (Throwable e) {
            LOGGER.warn("[modl] Cirrus menu system unavailable: {}", e.getMessage());
        }

        try {
            String backendUrl = bootConfig.isTestingApi()
                    ? "https://api.modl.top/v2"
                    : "https://api.modl.gg/v2";
            String panelUrl = null;

            if (bootConfig.getMode() == BootConfig.Mode.STANDALONE) {
                panelUrl = StartupClient.callStartupWithRetry(
                        bootConfig.getApiKey(), bootConfig.isTestingApi(),
                        new StartupRequest(PluginInfo.VERSION, "FABRIC",
                                server.getServerVersion(), server.getPlayerList().getMaxPlayers()),
                        PLUGIN_LOGGER);
                if (panelUrl == null) {
                    LOGGER.error("Failed to connect to modl.gg. Check your API key and network connection.");
                    return;
                }

                saveDefaultResources(dataFolder);

                boolean debugMode = false;
                boolean queryMojang = false;
                int syncPollingRate = 2;
                List<String> mutedCommands = List.of();
                Path configPath = dataFolder.resolve("config.yml");
                if (configPath.toFile().exists()) {
                    try {
                        Yaml yaml = new Yaml();
                        Map<String, Object> config = yaml.load(Files.newInputStream(configPath));
                        if (config != null) {
                            debugMode = Boolean.TRUE.equals(config.get("debug"));
                            Object serverObj = config.get("server");
                            if (serverObj instanceof Map<?, ?> serverMap) {
                                queryMojang = Boolean.TRUE.equals(serverMap.get("query_mojang"));
                            }
                            Object syncObj = config.get("sync");
                            if (syncObj instanceof Map<?, ?> syncMap) {
                                Object rate = syncMap.get("polling_rate");
                                if (rate instanceof Number number) {
                                    syncPollingRate = Math.max(1, number.intValue());
                                }
                            }
                            Object mutedObj = config.get("muted_commands");
                            if (mutedObj instanceof List<?> list) {
                                mutedCommands = list.stream().map(Object::toString).toList();
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to read config.yml: {}", e.getMessage());
                    }
                }

                FabricPlatform fabricPlatform = new FabricPlatform(server, dataFolder, PLUGIN_LOGGER);
                HttpManager httpManager = new HttpManager(
                        bootConfig.getApiKey(), panelUrl,
                        debugMode, bootConfig.isTestingApi(), queryMojang
                );
                ChatMessageCache chatMessageCache = new ChatMessageCache();
                pluginLoader = new PluginLoader(
                        fabricPlatform, dataFolder, chatMessageCache, httpManager, syncPollingRate);

                syncLampCommandsToServer(server, pluginLoader.getLamp());

                TicketCreator ticketCreator = (creatorUuid, creatorName, type, subject, description,
                                               reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
                    List<String> tags = tagsJoined == null || tagsJoined.isEmpty()
                            ? List.of()
                            : Arrays.asList(tagsJoined.split(","));
                    CreateTicketRequest request = new CreateTicketRequest(
                            creatorUuid, type, creatorName, subject, description,
                            reportedPlayerUuid, reportedPlayerName, priority, createdServer,
                            null, tags, replayUrl);
                    pluginLoader.getHttpClient().createTicket(request).thenAccept(response -> {
                        if (response.isSuccess()) {
                            LOGGER.info("[bridge] Report ticket created: {}", response.getTicketId());
                        } else {
                            LOGGER.warn("[bridge] Failed to create report ticket: {}", response.getMessage());
                        }
                    }).exceptionally(throwable -> {
                        LOGGER.warn("[bridge] Error creating report ticket: {}", throwable.getMessage());
                        return null;
                    });
                };

                standaloneTicketCreator = ticketCreator;
                standaloneDebugMode = debugMode;
                standaloneMutedCommands = mutedCommands;
                standaloneFabricPlatform = fabricPlatform;
            }

            if (bootConfig.getMode() == BootConfig.Mode.BRIDGE_ONLY) {
                writeBridgeConfigFromWizard(dataFolder);
            }

            bridgeComponent = new FabricBridgeComponent(
                    context, server, bootConfig.getApiKey(), backendUrl, panelUrl != null ? panelUrl : "", PLUGIN_LOGGER);
            bridgeComponent.enable(standaloneTicketCreator, bootConfig.getMode() == BootConfig.Mode.BRIDGE_ONLY);

            if (pluginLoader != null && standaloneFabricPlatform != null) {
                String serverName = bridgeComponent.getBridgeConfig() != null
                        ? bridgeComponent.getBridgeConfig().getServerName()
                        : "fabric-server";
                standaloneFabricPlatform.setServerName(serverName);

                if (bridgeComponent.getReplayService() != null) {
                    standaloneFabricPlatform.setReplayService(bridgeComponent.getReplayService());
                }

                FabricDirectStatWipeExecutor statWipeExecutor =
                        new FabricDirectStatWipeExecutor(bridgeComponent, serverName);
                pluginLoader.getSyncService().setStatWipeExecutor(statWipeExecutor);
            }

            if (pluginLoader != null) {
                FabricBridgeComponent bridge = bridgeComponent;
                pluginLoader.getBridgeService().setLocalHandler(new BridgeService.LocalBridgeHandler() {
                    @Override
                    public void onStaffModeEnter(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().enterStaffMode(staffUuid));
                    }

                    @Override
                    public void onStaffModeExit(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().exitStaffMode(staffUuid));
                    }

                    @Override
                    public void onVanishEnter(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().vanishFromBridge(staffUuid));
                    }

                    @Override
                    public void onVanishExit(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().unvanishFromBridge(staffUuid));
                    }

                    @Override
                    public void onFreezePlayer(String targetUuid, String staffUuid) {
                        server.execute(() -> bridge.getFabricFreezeHandler().freeze(targetUuid, staffUuid));
                    }

                    @Override
                    public void onUnfreezePlayer(String targetUuid) {
                        server.execute(() -> bridge.getFabricFreezeHandler().unfreeze(targetUuid));
                    }

                    @Override
                    public void onTargetRequest(String staffUuid, String targetUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().setTarget(staffUuid, targetUuid));
                    }
                });

                fabricListener = new FabricListener(
                        standaloneFabricPlatform, pluginLoader.getCache(), pluginLoader.getHttpClientHolder(),
                        pluginLoader.getChatMessageCache(), pluginLoader.getSyncService(),
                        pluginLoader.getLocaleManager(), pluginLoader.getLoginCache(),
                        standaloneMutedCommands, pluginLoader.getStaffChatService(),
                        pluginLoader.getChatManagementService(), pluginLoader.getMaintenanceService(),
                        pluginLoader.getFreezeService(), pluginLoader.getNetworkChatInterceptService(),
                        pluginLoader.getChatCommandLogService(), pluginLoader.getStaff2faService(),
                        pluginLoader.getConfigManager().getStaffChatConfig(),
                        pluginLoader.getBridgeService(), pluginLoader.getCachedProfileRegistry(),
                        standaloneDebugMode, server);
                fabricListener.register();

                PacketEventsAPI<?> packetEventsApi = requirePacketEventsApi();
                packetEventsApi.getEventManager().registerListener(
                        new FabricStaffModePacketListener(
                                bridgeComponent.getFabricStaffModeHandler(),
                                bridgeComponent.getFabricFreezeHandler()));
                packetEventsApi.getEventManager().registerListener(
                        new FabricCommandPacketListener(
                                pluginLoader.getCache(),
                                pluginLoader.getFreezeService(),
                                pluginLoader.getChatCommandLogService(),
                                pluginLoader.getLocaleManager(),
                                standaloneMutedCommands,
                                standaloneFabricPlatform.getServerName(),
                                server));
            }

            LOGGER.info("Successfully booted modl.gg platform plugin!");
        } catch (Exception e) {
            LOGGER.error("[modl] Failed to enable bridge component", e);
        }
    }

    private void syncLampCommandsToServer(MinecraftServer server, Lamp<?> lamp) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

            Field hooksField = Lamp.class.getDeclaredField("hooks");
            hooksField.setAccessible(true);
            Object hooksObj = hooksField.get(lamp);

            Object hooksList = null;
            for (Field field : hooksObj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(hooksObj);
                if (value instanceof List) {
                    hooksList = value;
                    break;
                }
            }

            if (hooksList != null) {
                for (Object hook : (List<?>) hooksList) {
                    if (hook.getClass().getName().contains("FabricCommandHooks")) {
                        Field rootField = hook.getClass().getDeclaredField("root");
                        rootField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        RootCommandNode<CommandSourceStack> root = (RootCommandNode<CommandSourceStack>) rootField.get(hook);

                        removeBrigadierNodes(dispatcher.getRoot(), root.getChildren());
                        for (CommandNode<CommandSourceStack> node : root.getChildren()) {
                            dispatcher.getRoot().addChild(node);
                        }
                        break;
                    }
                }
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        } catch (Exception e) {
            LOGGER.warn("[modl] Failed to sync commands to server: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeBrigadierNodes(CommandNode<?> parent,
                                      Collection<? extends CommandNode<?>> toRemove) {
        try {
            for (String fieldName : new String[]{"children", "literals", "arguments"}) {
                Field field = CommandNode.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Map<String, ?> map = (Map<String, ?>) field.get(parent);
                for (CommandNode<?> node : toRemove) {
                    map.remove(node.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[modl] Failed to remove existing command nodes: {}", e.getMessage());
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (pluginLoader != null) {
            pluginLoader.shutdown();
        }
        if (cirrus != null) {
            cirrus.shutdown();
        }
        if (bridgeComponent != null) {
            bridgeComponent.disable();
        }
    }

    private void writeBridgeConfigFromWizard(Path dataFolder) {
        BridgeWizardConfigWriter.writeBridgeOnlyConfig(dataFolder, bootConfig, PLUGIN_LOGGER);
    }

    private void saveDefaultResources(Path dataFolder) {
        saveResourceIfAbsent(dataFolder, "config.yml");
        Path localeDir = dataFolder.resolve("locale");
        localeDir.toFile().mkdirs();
        saveResourceIfAbsent(localeDir, "en_US.yml");
    }

    private void saveResourceIfAbsent(Path targetDir, String resourceName) {
        Path target = targetDir.resolve(resourceName);
        if (!target.toFile().exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (in != null) {
                    Files.copy(in, target);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to save default {}: {}", resourceName, e.getMessage());
            }
        }
    }

    private void logConfigurationError() {
        LOGGER.error("===============================================");
        LOGGER.error("modl.gg CONFIGURATION ERROR");
        LOGGER.error("===============================================");
        LOGGER.error("Could not load or create boot.yml.");
        LOGGER.error("Please configure manually in config/modl/boot.yml");
        LOGGER.error("or delete it to re-run the setup wizard.");
        LOGGER.error("===============================================");
    }
}
