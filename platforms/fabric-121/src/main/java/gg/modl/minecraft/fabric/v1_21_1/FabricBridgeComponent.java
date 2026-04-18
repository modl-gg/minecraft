package gg.modl.minecraft.fabric.v1_21_1;

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
import gg.modl.minecraft.fabric.v1_21_1.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v1_21_1.handler.FabricStaffModeHandler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.replay.recording.PacketRecorder;
import gg.modl.minecraft.replay.recording.RecordingConfig;
import gg.modl.minecraft.replay.recording.RecordingManager;
import lombok.Getter;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static gg.modl.minecraft.core.util.Java8Collections.*;

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
        fabricStaffModeHandler.startScoreboardUpdater();
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
                                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(targetUuid);
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
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
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

    private void startRecordingForPlayer(ServerPlayerEntity player) {
        if (recordingManager == null || packetRecorder == null) return;
        if (recordingManager.isRecording(player.getUuid())) return;

        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYaw(), pitch = player.getPitch();
        int radius = bridgeConfig.getReplayRadius();

        packetRecorder.trackSelf(player.getUuid(), player.getName().getString(), player.getId(),
                x, y, z, yaw, pitch);

        double radiusSq = (double) radius * radius;
        for (ServerPlayerEntity nearby : server.getPlayerManager().getPlayerList()) {
            if (nearby.equals(player)) continue;
            if (!nearby.getServerWorld().equals(player.getServerWorld())) continue;
            double dx = nearby.getX() - x, dy = nearby.getY() - y, dz = nearby.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            packetRecorder.getEntityTracker().trackPlayer(
                    player.getUuid(), nearby.getId(), nearby.getUuid(), nearby.getName().getString(),
                    nearby.getX(), nearby.getY(), nearby.getZ(), nearby.getYaw(), nearby.getPitch());
        }

        recordingManager.startRecording(player.getUuid(), player.getName().getString(),
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

        seedInventory(player);
        packetRecorder.emitInitialSelfEquipment(player.getUuid());
    }

    private void seedInventory(ServerPlayerEntity player) {
        Map<Integer, String> protocolSlots = new HashMap<>();
        Map<Integer, Integer> protocolCounts = new HashMap<>();

        for (int i = 0; i < player.getInventory().size() && i <= 40; i++) {
            ItemStack item = player.getInventory().getStack(i);
            String name = item.isEmpty() ? "air" : Registries.ITEM.getId(item.getItem()).getPath();
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

        packetRecorder.seedInventoryCache(player.getUuid(), protocolSlots, protocolCounts);
        packetRecorder.seedHeldSlot(player.getUuid(), player.getInventory().selectedSlot);
    }

    private static int getBlockStateId(BlockState state) {
        return net.minecraft.block.Block.getRawIdFromState(state);
    }

    private void trackSuccessfulBlockPlacement(ServerPlayerEntity player, World world,
                                               Hand hand, BlockHitResult hitResult) {
        if (recordingManager == null || recordingManager.getActiveRecordings().isEmpty()) {
            return;
        }

        ItemStack heldStack = player.getStackInHand(hand);
        if (!(heldStack.getItem() instanceof BlockItem)) {
            return;
        }

        ItemPlacementContext placementContext = new ItemPlacementContext(player, hand, heldStack, hitResult);
        if (!placementContext.canPlace()) {
            return;
        }

        BlockPos placePos = placementContext.getBlockPos().toImmutable();
        BlockState beforeState = world.getBlockState(placePos);
        UUID uuid = player.getUuid();

        server.execute(() -> {
            BlockState placedState = world.getBlockState(placePos);
            if (placedState.isAir() || placedState.equals(beforeState)) {
                return;
            }

            int stateId = getBlockStateId(placedState);
            recordingManager.enqueueBlockPlace(uuid, placePos.getX(), (short) placePos.getY(), placePos.getZ(), stateId);
        });
    }

    @Override
    protected void registerPlatformEvents() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            fabricFreezeHandler.onTick();
            fabricStaffModeHandler.onTick();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            fabricStaffModeHandler.onPlayerJoin(player);

            if (bridgeConfig != null && bridgeConfig.isReplayEnabled()
                    && bridgeConfig.isReplayAutoRecord() && recordingManager != null) {
                context.getScheduler().runForPlayerLater(player.getUuid(), () -> {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(player.getUuid());
                    if (p != null) startRecordingForPlayer(p);
                }, 40L);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
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
            if (fabricStaffModeHandler.isInStaffMode(player.getUuid())) return false;
            if (fabricFreezeHandler.isFrozen(player.getUuid())) return false;
            if (recordingManager != null && !recordingManager.getActiveRecordings().isEmpty()) {
                int stateId = getBlockStateId(state);
                recordingManager.enqueueBlockBreak(player.getUuid(), pos.getX(), (short) pos.getY(), pos.getZ(), stateId);
            }
            return true;
        });

        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
            if (fabricFreezeHandler.isFrozen(sp.getUuid())) return net.minecraft.util.ActionResult.FAIL;

            if (fabricStaffModeHandler.isInStaffMode(sp.getUuid())) {
                // Silent container viewing for vanished staff
                if (fabricStaffModeHandler.isVanished(sp.getUuid())) {
                    BlockPos pos = hitResult.getBlockPos();
                    net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof net.minecraft.inventory.Inventory container) {
                        fabricStaffModeHandler.openSilentContainer(sp, container, pos);
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
                return net.minecraft.util.ActionResult.FAIL;
            }

            trackSuccessfulBlockPlacement(sp, (World) world, hand, hitResult);

            return net.minecraft.util.ActionResult.PASS;
        });

        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND)
                return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);
            if (!(player instanceof ServerPlayerEntity sp))
                return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);
            if (!fabricStaffModeHandler.isInStaffMode(sp.getUuid()))
                return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);

            int slot = sp.getInventory().selectedSlot;
            Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(sp.getUuid());
            StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
            if (item != null && item.getAction() != null && !item.getAction().isEmpty()) {
                fabricStaffModeHandler.executeAction(sp, item);
                return net.minecraft.util.TypedActionResult.success(sp.getMainHandStack());
            }
            return net.minecraft.util.TypedActionResult.pass(net.minecraft.item.ItemStack.EMPTY);
        });

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return net.minecraft.util.ActionResult.PASS;
            if (!fabricStaffModeHandler.isInStaffMode(sp.getUuid())) return net.minecraft.util.ActionResult.PASS;

            if (entity instanceof ServerPlayerEntity targetPlayer) {
                int slot = sp.getInventory().selectedSlot;
                Map<Integer, StaffModeConfig.HotbarItem> hotbar = fabricStaffModeHandler.getActiveHotbar(sp.getUuid());
                StaffModeConfig.HotbarItem item = hotbar != null ? hotbar.get(slot) : null;
                if (item != null && "target_selector".equals(item.getAction())) {
                    fabricStaffModeHandler.setTarget(sp.getUuid().toString(), targetPlayer.getUuid().toString());
                    sp.sendMessage(Text.literal(localeManager.getMessage("staff_mode.target.now_targeting",
                            mapOf("player", targetPlayer.getName().getString()))), false);
                }
            }
            return net.minecraft.util.ActionResult.FAIL;
        });

        // World/dimension change for replay recording
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (recordingManager == null || packetRecorder == null) return;
            if (!bridgeConfig.isReplayEnabled() || !bridgeConfig.isReplayAutoRecord()) return;

            UUID playerId = player.getUuid();

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

                ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
                if (p != null && !recordingManager.isRecording(playerId)) {
                    startRecordingForPlayer(p);
                }
            }, 40L);
        });
    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        try {
            var dispatcher = server.getCommandManager().getDispatcher();
            dispatcher.register(
                    net.minecraft.server.command.CommandManager.literal("proxycmd")
                            .requires(source -> source.hasPermissionLevel(4))
                            .then(net.minecraft.server.command.CommandManager.argument("command",
                                            com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String cmd = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "command");
                                        client.sendMessage("PROXY_CMD", cmd);
                                        ctx.getSource().sendMessage(net.minecraft.text.Text.literal("Sent to proxy: " + cmd));
                                        return 1;
                                    })));
            for (var player : server.getPlayerManager().getPlayerList()) {
                server.getCommandManager().sendCommandTree(player);
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
}
