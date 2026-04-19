package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.BridgeTask;
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
import gg.modl.minecraft.replay.recording.PacketRecorder;
import gg.modl.minecraft.replay.recording.RecordingConfig;
import gg.modl.minecraft.replay.recording.RecordingManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class FabricBridgeComponent extends AbstractBridgeComponent {
    private final MinecraftServer server;
    private final FabricBridgePluginContext fabricContext;
    private FabricFreezeHandler fabricFreezeHandler;
    private FabricStaffModeHandler fabricStaffModeHandler;

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
        fabricStaffModeHandler = new FabricStaffModeHandler(
                server, bridgeConfig, fabricFreezeHandler, localeManager, staffModeConfig);
        fabricStaffModeHandler.startScoreboardUpdater();
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new FabricBridgeMessageHandler(
                server, fabricFreezeHandler, fabricStaffModeHandler, statWipeHandler, this);
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

        if (com.github.retrooper.packetevents.PacketEvents.getAPI() == null) {
            throw new IllegalStateException("PacketEvents Fabric mod did not initialize before replay recording setup");
        }

        RecordingConfig recordingConfig = new RecordingConfig() {
            @Override
            public int bufferDurationSeconds() {
                return config.getReplayBufferDuration();
            }

            @Override
            public int maxDurationSeconds() {
                return config.getReplayMaxDuration();
            }

            @Override
            public int radiusBlocks() {
                return config.getReplayRadius();
            }

            @Override
            public int moveThrottleMs() {
                return config.getReplayMoveThrottle();
            }

            @Override
            public String uploadEndpoint() {
                return backendUrl;
            }

            @Override
            public String uploadApiKey() {
                return apiKey;
            }

            @Override
            public String viewerBaseUrl() {
                return "";
            }

            @Override
            public String mcVersion() {
                return context.getMinecraftVersion();
            }
        };

        File replaysDir = context.getDataFolder().resolve("replays").toFile();
        if (config.isReplaySaveLocal()) {
            replaysDir.mkdirs();
        }

        recordingManager = new RecordingManager(recordingConfig, replaysDir, java.util.logging.Logger.getLogger("modl"));
        packetRecorder = new PacketRecorder(recordingManager, recordingConfig, java.util.logging.Logger.getLogger("modl"));
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
                if (files == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                for (File file : files) {
                    if (file.isFile() && now - file.lastModified() > ttlMs && file.delete()) {
                        pluginLogger.info("[bridge] Deleted expired replay file: " + file.getName());
                    }
                }
            }, 5, 5, TimeUnit.MINUTES);
        }

        pluginLogger.info("[bridge] Replay recording initialized (auto-record: " + config.isReplayAutoRecord()
                + ", save-local: " + config.isReplaySaveLocal() + ")");
    }

    private void startRecordingForPlayer(ServerPlayer player) {
        if (recordingManager == null || packetRecorder == null) {
            return;
        }
        if (recordingManager.isRecording(player.getUUID())) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        int radius = bridgeConfig.getReplayRadius();

        packetRecorder.trackSelf(player.getUUID(), player.getName().getString(), player.getId(), x, y, z, yaw, pitch);

        double radiusSq = (double) radius * radius;
        for (ServerPlayer nearby : server.getPlayerList().getPlayers()) {
            if (nearby.equals(player) || !nearby.level().equals(player.level())) {
                continue;
            }
            double dx = nearby.getX() - x;
            double dy = nearby.getY() - y;
            double dz = nearby.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
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
        return Block.getId(state);
    }

    private void trackSuccessfulBlockPlacement(ServerPlayer player, InteractionHand hand,
                                               net.minecraft.world.phys.BlockHitResult hitResult) {
        if (recordingManager == null || recordingManager.getActiveRecordings().isEmpty()) {
            return;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (!(heldStack.getItem() instanceof BlockItem)) {
            return;
        }

        BlockPlaceContext placementContext = new BlockPlaceContext(player, hand, heldStack, hitResult);
        if (!placementContext.canPlace()) {
            return;
        }

        BlockPos placePos = placementContext.getClickedPos().immutable();
        BlockState beforeState = player.level().getBlockState(placePos);
        UUID uuid = player.getUUID();

        server.execute(() -> {
            BlockState placedState = player.level().getBlockState(placePos);
            if (placedState.isAir() || placedState.equals(beforeState)) {
                return;
            }

            int stateId = getBlockStateId(placedState);
            recordingManager.enqueueBlockPlace(uuid, placePos.getX(), (short) placePos.getY(), placePos.getZ(), stateId);
        });
    }

    @Override
    protected void registerPlatformEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            fabricFreezeHandler.onTick();
            fabricStaffModeHandler.onTick();
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && fabricStaffModeHandler.isInStaffMode(player.getUUID())) {
                return false;
            }
            if (source.getDirectEntity() instanceof ServerPlayer player
                    && fabricStaffModeHandler.isInStaffMode(player.getUUID())) {
                return false;
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> {
            ServerPlayer player = handler.getPlayer();
            fabricStaffModeHandler.onPlayerJoin(player);

            if (bridgeConfig != null && bridgeConfig.isReplayEnabled()
                    && bridgeConfig.isReplayAutoRecord() && recordingManager != null) {
                context.getScheduler().runForPlayerLater(player.getUUID(), () -> {
                    ServerPlayer current = this.server.getPlayerList().getPlayer(player.getUUID());
                    if (current != null) {
                        startRecordingForPlayer(current);
                    }
                }, 40L);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
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
            if (fabricStaffModeHandler.isInStaffMode(player.getUUID())) return false;
            if (fabricFreezeHandler.isFrozen(player.getUUID())) return false;
            if (recordingManager != null && !recordingManager.getActiveRecordings().isEmpty()) {
                int stateId = getBlockStateId(state);
                recordingManager.enqueueBlockBreak(player.getUUID(), pos.getX(), (short) pos.getY(), pos.getZ(), stateId);
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (fabricFreezeHandler.isFrozen(staff.getUUID())) {
                return InteractionResult.FAIL;
            }

            if (fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                if (fabricStaffModeHandler.isVanished(staff.getUUID())) {
                    BlockPos pos = hitResult.getBlockPos();
                    var blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof net.minecraft.world.Container container) {
                        fabricStaffModeHandler.openSilentContainer(staff, container, pos);
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.FAIL;
            }

            trackSuccessfulBlockPlacement(staff, hand, hitResult);

            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }

            int slot = staff.getInventory().getSelectedSlot();
            Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(staff.getUUID());
            StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
            if (item != null && item.getAction() != null && !item.getAction().isEmpty()) {
                fabricStaffModeHandler.executeAction(staff, item);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }

            if (hand == InteractionHand.MAIN_HAND && entity instanceof ServerPlayer targetPlayer) {
                int slot = staff.getInventory().getSelectedSlot();
                Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(staff.getUUID());
                StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
                if (item != null && "target_selector".equals(item.getAction())) {
                    fabricStaffModeHandler.setTarget(staff.getUUID().toString(), targetPlayer.getUUID().toString());
                    staff.sendSystemMessage(Component.literal(localeManager.getMessage(
                            "staff_mode.target.now_targeting",
                            mapOf("player", targetPlayer.getName().getString()))), false);
                }
            }
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer staff)) {
                return InteractionResult.PASS;
            }
            if (!fabricStaffModeHandler.isInStaffMode(staff.getUUID())) {
                return InteractionResult.PASS;
            }
            return InteractionResult.FAIL;
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

                ServerPlayer currentPlayer = server.getPlayerList().getPlayer(playerId);
                if (currentPlayer != null && !recordingManager.isRecording(playerId)) {
                    startRecordingForPlayer(currentPlayer);
                }
            }, 40L);
        });
    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        try {
            var dispatcher = server.getCommands().getDispatcher();
            dispatcher.register(
                    net.minecraft.commands.Commands.literal("proxycmd")
                            .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_OWNERS))
                            .then(net.minecraft.commands.Commands.argument(
                                            "command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String command = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "command");
                                        client.sendMessage("PROXY_CMD", command);
                                        ctx.getSource().sendSystemMessage(Component.literal("Sent to proxy: " + command));
                                        return 1;
                                    })));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        } catch (Exception e) {
            pluginLogger.warning("[bridge] Failed to register proxy command: " + e.getMessage());
        }
    }

    @Override
    protected void onDisable() {
        if (replayCleanupTask != null) replayCleanupTask.cancel();
        if (recordingManager != null) recordingManager.stopAll();
        if (packetRecorder != null) packetRecorder.unregister();
        if (fabricStaffModeHandler != null) fabricStaffModeHandler.shutdown();
        ((FabricBridgeScheduler) fabricContext.getScheduler()).shutdown();
    }

    public FabricFreezeHandler getFabricFreezeHandler() {
        return fabricFreezeHandler;
    }

    public FabricStaffModeHandler getFabricStaffModeHandler() {
        return fabricStaffModeHandler;
    }
}
