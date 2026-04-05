package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.reporter.ModlBackendReplayUploader;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.fabric.v26.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v26.handler.FabricStaffModeHandler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.replay.recording.PacketRecorder;
import gg.modl.minecraft.replay.recording.RecordingConfig;
import gg.modl.minecraft.replay.recording.RecordingManager;
import lombok.Getter;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FabricBridgeComponent extends AbstractBridgeComponent {
    private final MinecraftServer server;
    private final FabricBridgePluginContext fabricContext;
    @Getter private FabricFreezeHandler fabricFreezeHandler;
    @Getter private FabricStaffModeHandler fabricStaffModeHandler;

    private RecordingManager recordingManager;
    private PacketRecorder packetRecorder;
    private BridgeTask replayCleanupTask;
    private final Map<UUID, Integer> worldChangeGeneration = new ConcurrentHashMap<>();

    public FabricBridgeComponent(FabricBridgePluginContext context, MinecraftServer server,
                                  String apiKey, String backendUrl, String panelUrl, PluginLogger pluginLogger) {
        super(context, apiKey, backendUrl, panelUrl, pluginLogger);
        this.server = server;
        this.fabricContext = context;
    }

    @Override
    protected void initFreezeHandler(BridgeLocaleManager localeManager) {
        fabricFreezeHandler = new FabricFreezeHandler(server, localeManager);
    }

    @Override
    protected void initStaffModeHandler(BridgeConfig bridgeConfig,
                                         BridgeLocaleManager localeManager,
                                         StaffModeConfig staffModeConfig) {
        fabricStaffModeHandler = new FabricStaffModeHandler(server, bridgeConfig, fabricFreezeHandler,
                localeManager, staffModeConfig);
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new FabricBridgeMessageHandler(server, fabricFreezeHandler, fabricStaffModeHandler,
                statWipeHandler, this);
    }

    @Override
    protected void onBridgeClientCreated(BridgeQueryClient client) {
        fabricStaffModeHandler.setBridgeClient(client);
        fabricFreezeHandler.setBridgeClient(client);
    }

    @Override
    protected void registerAntiCheatHooks(List<AntiCheatHook> hooks) {
        pluginLogger.info("[bridge] Fabric platform: anticheat hooks not available");
    }

    @Override
    protected void initReplayRecording(BridgeConfig config) {
        if (!config.isReplayEnabled()) {
            pluginLogger.info("[bridge] Replay recording disabled in config");
            return;
        }

        if (backendUrl == null || backendUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            pluginLogger.info("[bridge] Backend URL or API key not configured, replay capture unavailable");
            return;
        }

        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
        } catch (ClassNotFoundException e) {
            pluginLogger.warning("[bridge] PacketEvents not found, replay recording disabled");
            return;
        }

        if (com.github.retrooper.packetevents.PacketEvents.getAPI() == null) {
            pluginLogger.warning("[bridge] PacketEvents not initialized, replay recording disabled");
            return;
        }

        RecordingConfig recordingConfig = new RecordingConfig() {
            @Override public int bufferDurationSeconds() { return config.getReplayBufferDuration(); }
            @Override public int maxDurationSeconds() { return config.getReplayMaxDuration(); }
            @Override public int radiusBlocks() { return config.getReplayRadius(); }
            @Override public int moveThrottleMs() { return config.getReplayMoveThrottle(); }
            @Override public String uploadEndpoint() { return backendUrl; }
            @Override public String uploadApiKey() { return apiKey; }
            @Override public String viewerBaseUrl() { return ""; }
            @Override public String mcVersion() { return context.getMinecraftVersion(); }
        };

        File replaysDir = context.getDataFolder().resolve("replays").toFile();
        if (config.isReplaySaveLocal()) {
            replaysDir.mkdirs();
        }

        recordingManager = new RecordingManager(recordingConfig, replaysDir,
                java.util.logging.Logger.getLogger("modl"));
        packetRecorder = new PacketRecorder(recordingManager, recordingConfig,
                java.util.logging.Logger.getLogger("modl"));
        recordingManager.setPacketRecorder(packetRecorder);
        packetRecorder.register();

        String serverDomain = extractDomain(panelUrl);
        ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(
                backendUrl, apiKey, serverDomain, java.util.logging.Logger.getLogger("modl"));

        this.replayService = new ReplayService() {
            @Override
            public CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
                if (!recordingManager.isRecording(targetUuid)) {
                    return CompletableFuture.completedFuture(null);
                }

                packetRecorder.cleanupPlayer(targetUuid);

                return recordingManager.stopRecordingAsync(targetUuid)
                        .thenCompose(metadata -> {
                            File replayFile = metadata != null ? metadata.getOutputFile() : null;

                            if (config.isReplayAutoRecord()) {
                                context.getScheduler().runForPlayerLater(targetUuid, () -> {
                                    ServerPlayer player = server.getPlayerList().getPlayer(targetUuid);
                                    if (player != null) {
                                        startRecordingForPlayer(player);
                                    }
                                }, 40L);
                            }

                            if (replayFile == null || !replayFile.exists()) {
                                pluginLogger.warning("[bridge] No replay file found for " + targetName);
                                return CompletableFuture.completedFuture(null);
                            }

                            return uploader.uploadAsync(replayFile, recordingConfig.mcVersion())
                                    .thenApply(replayId -> {
                                        pluginLogger.info("[bridge] Replay uploaded for " + targetName + ": " + replayId);
                                        return replayId;
                                    })
                                    .whenComplete((replayId, ex) -> {
                                        if (ex != null) {
                                            pluginLogger.warning("[bridge] Replay upload failed for " + targetName + ": " + ex.getMessage());
                                        }
                                        if (!config.isReplaySaveLocal()) {
                                            replayFile.delete();
                                        }
                                    });
                        });
            }

            @Override
            public boolean isReplayAvailable(UUID playerUuid) {
                return recordingManager.isRecording(playerUuid);
            }
        };

        if (config.isReplayAutoRecord()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                startRecordingForPlayer(player);
            }
        }

        if (config.isReplaySaveLocal()) {
            long ttlMs = config.getReplayLocalTtl() * 60_000L;
            replayCleanupTask = context.getScheduler().runTimerAsync(() -> {
                File[] files = replaysDir.listFiles();
                if (files == null) return;
                long now = System.currentTimeMillis();
                for (File f : files) {
                    if (f.isFile() && now - f.lastModified() > ttlMs) {
                        if (f.delete()) pluginLogger.info("[bridge] Deleted expired replay file: " + f.getName());
                    }
                }
            }, 5, 5, TimeUnit.MINUTES);
        }

        pluginLogger.info("[bridge] Replay recording initialized (auto-record: " + config.isReplayAutoRecord()
                + ", save-local: " + config.isReplaySaveLocal() + ")");
    }

    private void startRecordingForPlayer(ServerPlayer player) {
        if (recordingManager == null || packetRecorder == null) return;
        if (recordingManager.isRecording(player.getUUID())) return;

        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();
        int radius = bridgeConfig.getReplayRadius();

        packetRecorder.trackSelf(player.getUUID(), player.getName().getString(), player.getId(),
                x, y, z, yaw, pitch);

        double radiusSq = (double) radius * radius;
        for (ServerPlayer nearby : server.getPlayerList().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (!nearby.level().equals(player.level())) continue;
            double dx = nearby.getX() - x, dy = nearby.getY() - y, dz = nearby.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            packetRecorder.getEntityTracker().trackPlayer(
                    player.getUUID(), nearby.getId(), nearby.getUUID(), nearby.getName().getString(),
                    nearby.getX(), nearby.getY(), nearby.getZ(), nearby.getYRot(), nearby.getXRot());
        }

        recordingManager.startRecording(player.getUUID(), player.getName().getString(),
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

        seedInventory(player);
        packetRecorder.emitInitialSelfEquipment(player.getUUID());
    }

    private void seedInventory(ServerPlayer player) {
        Map<Integer, String> protocolSlots = new HashMap<>();
        Map<Integer, Integer> protocolCounts = new HashMap<>();

        for (int i = 0; i < player.getInventory().getContainerSize() && i <= 40; i++) {
            ItemStack item = player.getInventory().getItem(i);
            String name = item.isEmpty() ? "air" : BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
            int amount = item.isEmpty() ? 0 : item.getCount();
            int protocolSlot;
            if (i <= 8) protocolSlot = 36 + i;
            else if (i <= 35) protocolSlot = i;
            else if (i == 36) protocolSlot = 8;
            else if (i == 37) protocolSlot = 7;
            else if (i == 38) protocolSlot = 6;
            else if (i == 39) protocolSlot = 5;
            else if (i == 40) protocolSlot = 45;
            else continue;
            protocolSlots.put(protocolSlot, name);
            protocolCounts.put(protocolSlot, amount);
        }

        packetRecorder.seedInventoryCache(player.getUUID(), protocolSlots, protocolCounts);
        packetRecorder.seedHeldSlot(player.getUUID(), player.getInventory().getSelectedSlot());
    }

    private static int getBlockStateId(BlockState state) {
        return Block.BLOCK_STATE_REGISTRY.getId(state);
    }

    @Override
    protected void registerPlatformEvents() {
        ServerTickEvents.END_SERVER_TICK.register(s -> fabricFreezeHandler.onTick());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer player = handler.getPlayer();
            fabricStaffModeHandler.onPlayerJoin(player);

            if (bridgeConfig != null && bridgeConfig.isReplayEnabled()
                    && bridgeConfig.isReplayAutoRecord() && recordingManager != null) {
                context.getScheduler().runForPlayerLater(player.getUUID(), () -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(player.getUUID());
                    if (p != null) startRecordingForPlayer(p);
                }, 40L);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            fabricStaffModeHandler.onPlayerQuit(player);
            fabricFreezeHandler.onPlayerQuit(uuid);
            if (violationTracker != null) violationTracker.resetPlayer(uuid);
            if (autoReporter != null) autoReporter.clearCooldown(uuid);
            worldChangeGeneration.remove(uuid);

            if (recordingManager != null && recordingManager.isRecording(uuid)) {
                recordingManager.stopRecordingAsync(uuid);
            }
            if (packetRecorder != null) {
                packetRecorder.disconnectPlayer(uuid);
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (recordingManager != null && !recordingManager.getActiveRecordings().isEmpty()) {
                int stateId = getBlockStateId(state);
                recordingManager.enqueueBlockBreak(player.getUUID(), pos.getX(), (short) pos.getY(), pos.getZ(), stateId);
            }
            return true;
        });

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (recordingManager == null || packetRecorder == null) return;
            if (!bridgeConfig.isReplayEnabled() || !bridgeConfig.isReplayAutoRecord()) return;

            UUID playerId = player.getUUID();

            if (recordingManager.isRecording(playerId)) {
                packetRecorder.cleanupPlayer(playerId);
                recordingManager.stopRecordingAsync(playerId);
            }
            packetRecorder.getEntityTracker().clearPlayer(playerId);

            int generation = worldChangeGeneration.merge(playerId, 1, Integer::sum);

            context.getScheduler().runForPlayerLater(playerId, () -> {
                Integer current = worldChangeGeneration.get(playerId);
                if (current == null || current != generation) return;
                worldChangeGeneration.remove(playerId);

                ServerPlayer p = server.getPlayerList().getPlayer(playerId);
                if (p != null && !recordingManager.isRecording(playerId)) {
                    startRecordingForPlayer(p);
                }
            }, 40L);
        });
    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        // TODO: Brigadier command registration via CommandRegistrationCallback
    }

    @Override
    protected void onDisable() {
        if (replayCleanupTask != null) replayCleanupTask.cancel();
        if (recordingManager != null) recordingManager.stopAll();
        if (packetRecorder != null) packetRecorder.unregister();
        if (fabricStaffModeHandler != null) fabricStaffModeHandler.shutdown();
        ((FabricBridgeScheduler) fabricContext.getScheduler()).shutdown();
    }
}
