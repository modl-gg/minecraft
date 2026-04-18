package gg.modl.minecraft.fabric.v1_21_1;

import dev.simplix.cirrus.fabric.CirrusFabric;
import gg.modl.minecraft.api.http.request.CreateTicketRequest;
import gg.modl.minecraft.api.http.request.StartupRequest;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.TicketCreator;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.boot.*;
import gg.modl.minecraft.core.HttpManager;
import gg.modl.minecraft.core.PluginLoader;
import gg.modl.minecraft.core.plugin.PluginInfo;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.util.PluginLogger;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Optional;

public class ModlFabricModImpl implements DedicatedServerModInitializer {
    public static final String MOD_ID = "modl";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final PluginLogger PLUGIN_LOGGER = new PluginLogger() {
        @Override public void info(String msg) { LOGGER.info(msg); }
        @Override public void warning(String msg) { LOGGER.warn(msg); }
        @Override public void severe(String msg) { LOGGER.error(msg); }
    };

    private FabricBridgeComponent bridgeComponent;
    private CirrusFabric cirrus;
    private BootConfig bootConfig;
    private PluginLoader pluginLoader;
    private FabricListener fabricListener;

    // Temporary fields to pass data from standalone block to post-bridge wiring
    private TicketCreator standaloneTicketCreator;
    private boolean standaloneDebugMode;
    private List<String> standaloneMutedCommands = List.of();
    private FabricPlatform standaloneFabricPlatform;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[modl] Initializing modl Fabric mod");

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
                    LOGGER.info("Loaded configuration from boot.yml (mode: {})", config.getMode().toYaml());
                    return config;
                }
            }

            Optional<BootConfig> migrated = BootConfigMigrator.migrateFromConfigYml(
                    dataFolder, PlatformType.FABRIC, PLUGIN_LOGGER);
            if (migrated.isPresent()) return migrated.get();

            LOGGER.info("No configuration found. Starting setup wizard...");
            ConsoleInput input = ConsoleInput.system(PLUGIN_LOGGER);
            SetupWizard wizard = new SetupWizard(PLUGIN_LOGGER, input, PlatformType.FABRIC);
            BootConfig config = wizard.run();

            if (config != null) {
                config.save(dataFolder);
                return config;
            }

            logConfigurationError();
            return null;
        } catch (IOException e) {
            LOGGER.error("Failed to load boot.yml: {}", e.getMessage());
            return null;
        }
    }

    private boolean isPacketEventsBootstrapped() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return com.github.retrooper.packetevents.PacketEvents.getAPI() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void onServerStarted(MinecraftServer server) {
        if (bootConfig == null) return;

        Path dataFolder = FabricLoader.getInstance().getConfigDir().resolve("modl");
        FabricBridgePluginContext context = new FabricBridgePluginContext(server, dataFolder);

        if (!isPacketEventsBootstrapped()) {
            LOGGER.error("[modl] PacketEvents was not bootstrapped by Fabric; skipping Cirrus/menu startup");
        } else {
            try {
                cirrus = new CirrusFabric(server);
                cirrus.init();
            } catch (Throwable e) {
                LOGGER.warn("[modl] Cirrus menu system unavailable: {}", e.getMessage());
            }
        }

        try {
            String backendUrl = bootConfig.isTestingApi()
                    ? "https://api.modl.top/v2" : "https://api.modl.gg/v2";
            String panelUrl = null;

            if (bootConfig.getMode() == BootConfig.Mode.STANDALONE) {
                panelUrl = StartupClient.callStartupWithRetry(
                        bootConfig.getApiKey(), bootConfig.isTestingApi(),
                        new StartupRequest(PluginInfo.VERSION, "FABRIC",
                                server.getVersion(), server.getMaxPlayerCount()),
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
                        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
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
                                if (rate instanceof Number n) syncPollingRate = Math.max(1, n.intValue());
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
                pluginLoader = new PluginLoader(fabricPlatform, dataFolder, chatMessageCache, httpManager, syncPollingRate);

                syncLampCommandsToServer(server, pluginLoader.getLamp());

                TicketCreator ticketCreator = (creatorUuid, creatorName, type, subject, description,
                                               reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer, replayUrl) -> {
                    List<String> tags = tagsJoined == null || tagsJoined.isEmpty() ? List.of() : Arrays.asList(tagsJoined.split(","));
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

            bridgeComponent = new FabricBridgeComponent(context, server,
                    bootConfig.getApiKey(), backendUrl, panelUrl != null ? panelUrl : "", PLUGIN_LOGGER);
            bridgeComponent.enable(standaloneTicketCreator, bootConfig.getMode() == BootConfig.Mode.BRIDGE_ONLY);

            if (pluginLoader != null && standaloneFabricPlatform != null) {
                String serverName = bridgeComponent.getBridgeConfig() != null
                        ? bridgeComponent.getBridgeConfig().getServerName() : "fabric-server";
                standaloneFabricPlatform.setServerName(serverName);

                if (bridgeComponent.getReplayService() != null) {
                    standaloneFabricPlatform.setReplayService(bridgeComponent.getReplayService());
                }

                FabricDirectStatWipeExecutor statWipeExecutor = new FabricDirectStatWipeExecutor(bridgeComponent, serverName);
                pluginLoader.getSyncService().setStatWipeExecutor(statWipeExecutor);
            }

            if (pluginLoader != null) {
                FabricBridgeComponent bridge = bridgeComponent;
                pluginLoader.getBridgeService().setLocalHandler(new BridgeService.LocalBridgeHandler() {
                    @Override public void onStaffModeEnter(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().enterStaffMode(staffUuid));
                    }
                    @Override public void onStaffModeExit(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().exitStaffMode(staffUuid));
                    }
                    @Override public void onVanishEnter(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().vanishFromBridge(staffUuid));
                    }
                    @Override public void onVanishExit(String staffUuid) {
                        server.execute(() -> bridge.getFabricStaffModeHandler().unvanishFromBridge(staffUuid));
                    }
                    @Override public void onFreezePlayer(String targetUuid, String staffUuid) {
                        server.execute(() -> bridge.getFabricFreezeHandler().freeze(targetUuid, staffUuid));
                    }
                    @Override public void onUnfreezePlayer(String targetUuid) {
                        server.execute(() -> bridge.getFabricFreezeHandler().unfreeze(targetUuid));
                    }
                    @Override public void onTargetRequest(String staffUuid, String targetUuid) {
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

                if (com.github.retrooper.packetevents.PacketEvents.getAPI() != null) {
                    com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(
                            new FabricStaffModePacketListener(
                                    bridgeComponent.getFabricStaffModeHandler(),
                                    bridgeComponent.getFabricFreezeHandler()));
                    com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(
                            new FabricCommandPacketListener(
                                    pluginLoader.getCache(),
                                    pluginLoader.getFreezeService(),
                                    pluginLoader.getChatCommandLogService(),
                                    pluginLoader.getLocaleManager(),
                                    standaloneMutedCommands,
                                    standaloneFabricPlatform.getServerName(),
                                    server));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[modl] Failed to enable bridge component", e);
        }
    }

    private void syncLampCommandsToServer(MinecraftServer server, revxrsal.commands.Lamp<?> lamp) {
        try {
            var dispatcher = server.getCommandManager().getDispatcher();

            // Get the Hooks object from Lamp
            var hooksField = revxrsal.commands.Lamp.class.getDeclaredField("hooks");
            hooksField.setAccessible(true);
            Object hooksObj = hooksField.get(lamp);

            // Hooks stores registered hooks internally — find the list field
            Object hooksList = null;
            for (var field : hooksObj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object val = field.get(hooksObj);
                if (val instanceof java.util.List) {
                    hooksList = val;
                    break;
                }
            }

            if (hooksList != null) {
                for (Object hook : (java.util.List<?>) hooksList) {
                    if (hook.getClass().getName().contains("FabricCommandHooks")) {
                        var rootField = hook.getClass().getDeclaredField("root");
                        rootField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        var root = (com.mojang.brigadier.tree.RootCommandNode<net.minecraft.server.command.ServerCommandSource>) rootField.get(hook);

                        // Remove existing nodes first (Brigadier merges instead of replacing)
                        removeBrigadierNodes(dispatcher.getRoot(), root.getChildren());
                        for (var node : root.getChildren()) {
                            dispatcher.getRoot().addChild(node);
                        }
                        LOGGER.info("[modl] Synced {} commands to server dispatcher", root.getChildren().size());
                        break;
                    }
                }
            }

            // Resend command tree to all connected players
            for (var player : server.getPlayerManager().getPlayerList()) {
                server.getCommandManager().sendCommandTree(player);
            }
        } catch (Exception e) {
            LOGGER.warn("[modl] Failed to sync commands to server: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeBrigadierNodes(com.mojang.brigadier.tree.CommandNode<?> parent,
                                       java.util.Collection<? extends com.mojang.brigadier.tree.CommandNode<?>> toRemove) {
        try {
            // Brigadier stores children in three internal maps: children, literals, arguments
            // We need to remove from all three to fully replace a command
            for (String fieldName : new String[]{"children", "literals", "arguments"}) {
                var field = com.mojang.brigadier.tree.CommandNode.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                var map = (java.util.Map<String, ?>) field.get(parent);
                for (var node : toRemove) {
                    map.remove(node.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[modl] Failed to remove existing command nodes: {}", e.getMessage());
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (pluginLoader != null) pluginLoader.shutdown();
        if (cirrus != null) cirrus.shutdown();
        if (bridgeComponent != null) bridgeComponent.disable();
    }

    private void writeBridgeConfigFromWizard(Path dataFolder) {
        if (bootConfig.getWizardProxyHost() == null) return;
        try {
            BridgeConfig bridgeConfig = BridgeConfig.load(dataFolder);
            bridgeConfig.setApiKey(bootConfig.getApiKey());
            bridgeConfig.updateProxyConnection(
                    bootConfig.getWizardProxyHost(), bootConfig.getWizardProxyPort());
            bridgeConfig.save(dataFolder);
        } catch (IOException e) {
            LOGGER.warn("Failed to write bridge-config.yml: {}", e.getMessage());
        }
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
                if (in != null) Files.copy(in, target);
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
