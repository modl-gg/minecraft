package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.AbstractBridgeComponent;
import gg.modl.minecraft.bridge.BridgePluginContext;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.config.StaffModeConfig;
import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.ModlBackendReplayUploader;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.spigot.bridge.command.ProxyCmdCommand;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.reporter.hook.GrimHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.PolarHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.VulcanHook;
import gg.modl.minecraft.replay.format.events.BlockChangeEvent;
import gg.modl.minecraft.replay.recording.PacketRecorder;
import gg.modl.minecraft.replay.recording.RecordingConfig;
import gg.modl.minecraft.replay.recording.RecordingManager;
import lombok.Getter;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BridgeComponent extends AbstractBridgeComponent implements Listener {
    private final JavaPlugin plugin;
    @Getter private FreezeHandler freezeHandler;
    @Getter private StaffModeHandler staffModeHandler;

    private boolean polarAvailable;

    private RecordingManager recordingManager;
    private PacketRecorder packetRecorder;
    private gg.modl.minecraft.bridge.BridgeTask replayCleanupTask;
    private final Map<UUID, Integer> worldChangeGeneration = new ConcurrentHashMap<>();

    public BridgeComponent(JavaPlugin plugin, String apiKey, String backendUrl, String panelUrl, PluginLogger logger) {
        super(new SpigotBridgePluginContext(plugin), apiKey, backendUrl, panelUrl, logger);
        this.plugin = plugin;
    }

    public void onLoad() {
        try {
            Class.forName("top.polar.api.loader.LoaderApi");
            polarAvailable = true;
            top.polar.api.loader.LoaderApi.registerEnableCallback(() -> {
                if (plugin.isEnabled()) {
                    hookPolar();
                }
            });
        } catch (ClassNotFoundException ignored) {}
    }

    @Override
    protected void initFreezeHandler(BridgeLocaleManager localeManager) {
        freezeHandler = new FreezeHandler(plugin, localeManager, context.getScheduler());
        freezeHandler.register();
    }

    @Override
    protected void initStaffModeHandler(BridgeConfig bridgeConfig,
                                         BridgeLocaleManager localeManager,
                                         StaffModeConfig staffModeConfig) {
        staffModeHandler = new StaffModeHandler(plugin, bridgeConfig, freezeHandler, localeManager, staffModeConfig, context.getScheduler());
        staffModeHandler.register();
        freezeHandler.setStaffModeHandler(staffModeHandler);
    }

    @Override
    protected BridgeMessageHandler createMessageHandler() {
        return new SpigotBridgeMessageHandler(plugin, freezeHandler, staffModeHandler,
                statWipeHandler, this, context.getScheduler());
    }

    @Override
    protected void onBridgeClientCreated(BridgeQueryClient client) {
        staffModeHandler.setBridgeClient(client);
        freezeHandler.setBridgeClient(client);
    }

    @Override
    protected void registerAntiCheatHooks(List<AntiCheatHook> hooks) {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") != null) {
            GrimHook grimHook = new GrimHook(plugin, bridgeConfig, violationTracker, autoReporter);
            grimHook.register();
            hooks.add(grimHook);
        }

        if (Bukkit.getPluginManager().getPlugin("Vulcan") != null) {
            VulcanHook vulcanHook = new VulcanHook(plugin, bridgeConfig, violationTracker, autoReporter);
            vulcanHook.register();
            hooks.add(vulcanHook);
        }

        if (!polarAvailable) {
            PolarHook polarHook = new PolarHook(plugin, bridgeConfig, violationTracker, autoReporter);
            if (polarHook.isAvailable()) {
                polarHook.register();
                hooks.add(polarHook);
            }
        }

        if (hooks.isEmpty() && !polarAvailable) {
            pluginLogger.warning("[bridge] No anticheat plugins detected. Install GrimAC, Vulcan, or Polar for anticheat reporting.");
        }
    }

    private void hookPolar() {
        if (violationTracker == null || autoReporter == null) {
            pluginLogger.warning("Polar enable callback fired but bridge is not fully initialized");
            return;
        }

        PolarHook polarHook = new PolarHook(plugin, bridgeConfig, violationTracker, autoReporter);
        polarHook.register();
        hooks.add(polarHook);
    }

    @Override
    protected void initReplayRecording(BridgeConfig config) {
        if (!config.isReplayEnabled()) {
            return;
        }

        if (backendUrl == null || backendUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
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

        File replaysDir = new File(plugin.getDataFolder(), "replays");
        if (config.isReplaySaveLocal()) {
            replaysDir.mkdirs();
        }

        recordingManager = new RecordingManager(recordingConfig, replaysDir, plugin.getLogger());
        packetRecorder = new PacketRecorder(recordingManager, recordingConfig, plugin.getLogger());
        recordingManager.setPacketRecorder(packetRecorder);
        packetRecorder.register();

        String serverDomain = extractDomain(panelUrl);
        ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(backendUrl, apiKey, serverDomain, plugin.getLogger());

        this.replayService = new ReplayService() {
            @Override
            public CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName) {
                if (!recordingManager.isRecording(targetUuid)) {
                    return CompletableFuture.completedFuture(ReplayCaptureResult.noActiveRecording());
                }

                packetRecorder.cleanupPlayer(targetUuid);

                return recordingManager.stopRecordingAsync(targetUuid)
                        .thenCompose(metadata -> {
                            File replayFile = metadata != null ? metadata.getOutputFile() : null;

                            if (config.isReplayAutoRecord()) {
                                context.getScheduler().runForPlayerLater(targetUuid, () -> {
                                    Player player = Bukkit.getPlayer(targetUuid);
                                    if (player != null && player.isOnline()) {
                                        startRecordingForPlayer(player);
                                    }
                                }, 40L);
                            }

                            if (replayFile == null || !replayFile.exists()) {
                                pluginLogger.warning("[bridge] No replay file found for " + targetName + " after stopping recording");
                                return CompletableFuture.completedFuture(ReplayCaptureResult.error());
                            }

                            return uploader.uploadAsync(replayFile, recordingConfig.mcVersion())
                                    .thenApply(ReplayCaptureResult::ok)
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
            public ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
                return recordingManager.isRecording(playerUuid)
                        ? ReplayCaptureStatus.OK
                        : ReplayCaptureStatus.NO_ACTIVE_RECORDING;
            }
        };

        if (config.isReplayAutoRecord()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
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
                        f.delete();
                    }
                }
            }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
        }
    }

    @Override
    protected void registerPlatformEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void registerProxyCommand(BridgeQueryClient client) {
        plugin.getCommand("proxycmd").setExecutor(new ProxyCmdCommand(plugin, localeManager, client));
    }

    @Override
    protected void onDisable() {
        if (replayCleanupTask != null) {
            replayCleanupTask.cancel();
        }
        if (recordingManager != null) {
            recordingManager.stopAll();
        }
        if (packetRecorder != null) {
            packetRecorder.unregister();
        }
        if (staffModeHandler != null) staffModeHandler.shutdown();
    }

    private void startRecordingForPlayer(Player player) {
        if (recordingManager == null || packetRecorder == null) return;
        if (recordingManager.isRecording(player.getUniqueId())) return;

        Location loc = player.getLocation();
        int radius = bridgeConfig.getReplayRadius();

        packetRecorder.trackSelf(player.getUniqueId(), player.getName(), player.getEntityId(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        double radiusSq = (double) radius * radius;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.equals(player)) continue;
            Location nLoc = nearby.getLocation();
            if (!nLoc.getWorld().equals(loc.getWorld())) continue;
            if (nLoc.distanceSquared(loc) > radiusSq) continue;
            packetRecorder.getEntityTracker().trackPlayer(
                    player.getUniqueId(), nearby.getEntityId(), nearby.getUniqueId(), nearby.getName(),
                    nLoc.getX(), nLoc.getY(), nLoc.getZ(),
                    nLoc.getYaw(), nLoc.getPitch());
        }

        recordingManager.startRecording(
                player.getUniqueId(), player.getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );

        seedInventoryFromBukkit(player);
        packetRecorder.emitInitialSelfEquipment(player.getUniqueId());
    }

    private void seedInventoryFromBukkit(Player player) {
        PlayerInventory inv = player.getInventory();
        Map<Integer, String> protocolSlots = new HashMap<>();
        Map<Integer, Integer> protocolCounts = new HashMap<>();

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && i <= 40; i++) {
            ItemStack item = contents[i];
            String name = (item != null && item.getType() != Material.AIR)
                    ? item.getType().name().toLowerCase() : "air";
            int amount = (item != null && item.getType() != Material.AIR)
                    ? item.getAmount() : 0;
            int protocolSlot;
            if (i <= 8) {
                protocolSlot = 36 + i;
            } else if (i <= 35) {
                protocolSlot = i;
            } else if (i == 36) {
                protocolSlot = 8;
            } else if (i == 37) {
                protocolSlot = 7;
            } else if (i == 38) {
                protocolSlot = 6;
            } else if (i == 39) {
                protocolSlot = 5;
            } else if (i == 40) {
                protocolSlot = 45;
            } else {
                continue;
            }
            protocolSlots.put(protocolSlot, name);
            protocolCounts.put(protocolSlot, amount);
        }

        packetRecorder.seedInventoryCache(player.getUniqueId(), protocolSlots, protocolCounts);
        packetRecorder.seedHeldSlot(player.getUniqueId(), inv.getHeldItemSlot());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bridgeConfig.isReplayEnabled() && bridgeConfig.isReplayAutoRecord() && recordingManager != null) {
            context.getScheduler().runForPlayerLater(event.getPlayer().getUniqueId(), () -> {
                if (event.getPlayer().isOnline()) {
                    startRecordingForPlayer(event.getPlayer());
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!bridgeConfig.isReplayEnabled() || !bridgeConfig.isReplayAutoRecord()) return;
        if (recordingManager == null || packetRecorder == null) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

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

            if (player.isOnline() && !recordingManager.isRecording(playerId)) {
                startRecordingForPlayer(player);
            }
        }, 40L);
    }

    static int resolveBlockStateId(Block block, Function<Object, Integer> modernResolver,
                                   BiFunction<Material, Byte, Integer> legacyResolver) {
        try {
            Method getBlockData = block.getClass().getMethod("getBlockData");
            Object blockData = getBlockData.invoke(block);
            if (blockData != null) {
                try {
                    return modernResolver.apply(blockData);
                } catch (RuntimeException ignored) {}
            }
        } catch (ReflectiveOperationException ignored) {}

        return legacyResolver.apply(block.getType(), block.getData());
    }

    static int resolveBlockStateId(Block block) {
        return resolveBlockStateId(block, BridgeComponent::resolveModernBlockStateId, BridgeComponent::resolveLegacyBlockStateId);
    }

    private static int resolveModernBlockStateId(Object blockData) {
        try {
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Method converter = SpigotConversionUtil.class.getMethod("fromBukkitBlockData", blockDataClass);
            return ((com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState) converter.invoke(null, blockData)).getGlobalId();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to convert Bukkit BlockData to PacketEvents state", e);
        }
    }

    @SuppressWarnings("deprecation")
    private static int resolveLegacyBlockStateId(Material material, byte data) {
        return SpigotConversionUtil.fromBukkitMaterialData(new MaterialData(material, data)).getGlobalId();
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (recordingManager == null) return;
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        int stateId = resolveBlockStateId(block);
        recordingManager.enqueueEvent(player.getUniqueId(),
                new BlockChangeEvent(0, block.getX(), (short) block.getY(), block.getZ(), stateId));
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (recordingManager == null) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        int previousStateId = resolveBlockStateId(block);
        recordingManager.enqueueEvent(player.getUniqueId(),
                new BlockChangeEvent(0, block.getX(), (short) block.getY(), block.getZ(), previousStateId));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (violationTracker != null) violationTracker.resetPlayer(playerId);
        if (autoReporter != null) autoReporter.clearCooldown(playerId);
        worldChangeGeneration.remove(playerId);

        if (recordingManager != null && recordingManager.isRecording(playerId)) {
            recordingManager.stopRecordingAsync(playerId);
        }
        if (packetRecorder != null) {
            packetRecorder.disconnectPlayer(playerId);
        }
    }
}
