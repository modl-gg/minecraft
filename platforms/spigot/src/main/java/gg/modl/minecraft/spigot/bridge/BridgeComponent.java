package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.util.YamlMergeUtil;
import gg.modl.minecraft.spigot.bridge.command.ProxyCmdCommand;
import gg.modl.minecraft.spigot.bridge.command.ReplayCommand;
import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import gg.modl.minecraft.spigot.bridge.config.StaffModeConfig;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.spigot.bridge.query.BridgeQueryServer;
import gg.modl.minecraft.spigot.bridge.reporter.AutoReporter;
import gg.modl.minecraft.spigot.bridge.reporter.ModlBackendReplayUploader;
import gg.modl.minecraft.spigot.bridge.reporter.TicketCreator;
import gg.modl.minecraft.spigot.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.spigot.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.GrimHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.PolarHook;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import gg.modl.replay.recording.PacketRecorder;
import gg.modl.replay.recording.RecordingConfig;
import gg.modl.replay.recording.RecordingManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BridgeComponent implements Listener {
    private final JavaPlugin plugin;
    private final String apiKey;
    private final String backendUrl;
    private final String panelUrl;
    private final PluginLogger logger;
    private final List<AntiCheatHook> hooks = new ArrayList<>();

    @Getter private BridgeConfig bridgeConfig;
    @Getter private StatWipeHandler statWipeHandler;
    @Getter private FreezeHandler freezeHandler;
    @Getter private StaffModeHandler staffModeHandler;
    @Getter private BridgeQueryServer queryServer;

    private ViolationTracker violationTracker;
    private AutoReporter autoReporter;
    private boolean polarAvailable;
    @Getter private ReplayService replayService;

    // Replay recording (initialized directly, no reflection)
    private RecordingManager recordingManager;
    private PacketRecorder packetRecorder;

    public BridgeComponent(JavaPlugin plugin, String apiKey, String backendUrl, String panelUrl, PluginLogger logger) {
        this.plugin = plugin;
        this.apiKey = apiKey;
        this.backendUrl = backendUrl;
        this.panelUrl = panelUrl;
        this.logger = logger;
    }

    /**
     * Called during plugin onLoad() for early registrations (e.g. Polar LoaderApi).
     */
    public void onLoad() {
        try {
            Class.forName("top.polar.api.loader.LoaderApi");
            polarAvailable = true;
            top.polar.api.loader.LoaderApi.registerEnableCallback(() -> {
                if (plugin.isEnabled()) {
                    hookPolar();
                }
            });
            logger.info("Polar detected, registered enable callback");
        } catch (ClassNotFoundException ignored) {}
    }

    /**
     * Enable bridge component.
     *
     * @param ticketCreator the ticket creator to use (direct HTTP in standalone, TCP in bridge-only)
     */
    public void enable(TicketCreator ticketCreator) {
        // Save default bridge-config.yml if not present, then merge new keys
        if (!BridgeConfig.exists(plugin.getDataFolder().toPath())) {
            plugin.saveResource("bridge-config.yml", false);
        }
        YamlMergeUtil.mergeWithDefaults("/bridge-config.yml",
                plugin.getDataFolder().toPath().resolve("bridge-config.yml"), logger);

        try {
            bridgeConfig = BridgeConfig.load(plugin.getDataFolder().toPath());
        } catch (IOException e) {
            logger.severe("[bridge] Failed to load bridge-config.yml: " + e.getMessage());
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.setApiKey(apiKey);

        BridgeLocaleManager localeManager = new BridgeLocaleManager(plugin.getLogger());

        // Save default staff_mode.yml if not present, then merge new keys
        if (!plugin.getDataFolder().toPath().resolve("staff_mode.yml").toFile().exists()) {
            plugin.saveResource("staff_mode.yml", false);
        }
        YamlMergeUtil.mergeWithDefaults("/staff_mode.yml",
                plugin.getDataFolder().toPath().resolve("staff_mode.yml"), logger);

        violationTracker = new ViolationTracker();
        violationTracker.startCleanupTask(plugin);
        statWipeHandler = new StatWipeHandler(plugin, bridgeConfig);

        freezeHandler = new FreezeHandler(plugin, localeManager);
        freezeHandler.register();

        StaffModeConfig staffModeConfig = new StaffModeConfig(plugin.getDataFolder().toPath(), plugin.getLogger());
        staffModeHandler = new StaffModeHandler(plugin, bridgeConfig, freezeHandler, localeManager, staffModeConfig);
        staffModeHandler.register();
        freezeHandler.setStaffModeHandler(staffModeHandler);

        if (bridgeConfig.isQueryEnabled()) {
            queryServer = new BridgeQueryServer(
                    bridgeConfig.getQueryPort(),
                    bridgeConfig.getApiKey(),
                    statWipeHandler,
                    freezeHandler,
                    staffModeHandler,
                    plugin
            );
            queryServer.start();
            staffModeHandler.setQueryServer(queryServer);
            freezeHandler.setQueryServer(queryServer);
        }

        autoReporter = new AutoReporter(plugin, bridgeConfig, ticketCreator, violationTracker);
        Bukkit.getPluginManager().registerEvents(this, plugin);

        initializeReplayRecording();
        if (replayService != null) {
            autoReporter.setReplayService(replayService);
        }
        if (queryServer != null && replayService != null) {
            queryServer.setReplayService(replayService);
        }
        registerAntiCheatHooks();

        if (queryServer != null) {
            plugin.getCommand("proxycmd").setExecutor(new ProxyCmdCommand(plugin, localeManager, queryServer));
        }

        if (replayService != null && plugin.getCommand("replay") != null) {
            ReplayCommand replayCommand = new ReplayCommand(replayService, panelUrl);
            plugin.getCommand("replay").setExecutor(replayCommand);
            plugin.getCommand("replay").setTabCompleter(replayCommand);
        }

        logger.info("[bridge] Enabled with " + hooks.size() + " anticheat hook(s)"
                + (polarAvailable ? " (Polar pending callback)" : "")
                + (replayService != null ? " + replay capture" : ""));
    }

    public void disable() {
        // Stop all replay recordings
        if (recordingManager != null) {
            recordingManager.stopAll();
        }
        if (packetRecorder != null) {
            packetRecorder.unregister();
        }

        if (staffModeHandler != null) staffModeHandler.shutdown();
        if (queryServer != null) queryServer.shutdown();
        if (violationTracker != null) violationTracker.stopCleanupTask();

        hooks.forEach(AntiCheatHook::unregister);
        hooks.clear();

        logger.info("[bridge] Disabled");
    }

    private void registerAntiCheatHooks() {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") != null) {
            GrimHook grimHook = new GrimHook(plugin, bridgeConfig, violationTracker, autoReporter);
            grimHook.register();
            hooks.add(grimHook);
        }

        if (!polarAvailable) {
            PolarHook polarHook = new PolarHook(plugin, bridgeConfig, violationTracker, autoReporter);
            if (polarHook.isAvailable()) {
                polarHook.register();
                hooks.add(polarHook);
            }
        }

        if (hooks.isEmpty() && !polarAvailable) {
            logger.warning("[bridge] No anticheat plugins detected. Install GrimAC or Polar for anticheat reporting.");
        }
    }

    private void hookPolar() {
        if (violationTracker == null || autoReporter == null) {
            logger.warning("Polar enable callback fired but bridge is not fully initialized");
            return;
        }

        PolarHook polarHook = new PolarHook(plugin, bridgeConfig, violationTracker, autoReporter);
        polarHook.register();
        hooks.add(polarHook);
    }

    /**
     * Initializes the replay recording system directly using the modl-replay-recording library.
     * Creates RecordingManager + PacketRecorder, and builds a ReplayService that uploads to modl-backend.
     */
    private void initializeReplayRecording() {
        if (!bridgeConfig.isReplayEnabled()) {
            logger.info("[bridge] Replay recording disabled in config");
            return;
        }

        if (backendUrl == null || backendUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            logger.info("[bridge] Backend URL or API key not configured, replay capture unavailable");
            return;
        }

        try {
            // Check if PacketEvents is available
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
        } catch (ClassNotFoundException e) {
            logger.warning("[bridge] PacketEvents not found, replay recording disabled");
            return;
        }

        RecordingConfig recordingConfig = new RecordingConfig() {
            @Override public int bufferDurationSeconds() { return bridgeConfig.getReplayBufferDuration(); }
            @Override public int maxDurationSeconds() { return bridgeConfig.getReplayMaxDuration(); }
            @Override public int radiusBlocks() { return bridgeConfig.getReplayRadius(); }
            @Override public int moveThrottleMs() { return bridgeConfig.getReplayMoveThrottle(); }
            @Override public String uploadEndpoint() { return backendUrl; }
            @Override public String uploadApiKey() { return apiKey; }
            @Override public String viewerBaseUrl() { return ""; }
            @Override public String mcVersion() {
                String bukkit = Bukkit.getBukkitVersion();
                int dash = bukkit.indexOf('-');
                return dash > 0 ? bukkit.substring(0, dash) : bukkit;
            }
        };

        File replaysDir = new File(plugin.getDataFolder(), "replays");
        replaysDir.mkdirs();

        recordingManager = new RecordingManager(recordingConfig, replaysDir, plugin.getLogger());
        packetRecorder = new PacketRecorder(recordingManager, recordingConfig, plugin.getLogger());
        recordingManager.setPacketRecorder(packetRecorder);
        packetRecorder.register();

        // Upload via modl-backend
        String serverDomain = extractDomain(panelUrl);
        ModlBackendReplayUploader uploader = new ModlBackendReplayUploader(backendUrl, apiKey, serverDomain, plugin.getLogger());

        this.replayService = new ReplayService() {
            @Override
            public CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
                if (!recordingManager.isRecording(targetUuid)) {
                    return CompletableFuture.completedFuture(null);
                }

                packetRecorder.cleanupPlayer(targetUuid);

                // Stop recording async (drains + flushes on writer thread, no main-thread I/O)
                return recordingManager.stopRecordingAsync(targetUuid)
                        .thenCompose(metadata -> {
                            File replayFile = metadata != null ? metadata.getOutputFile() : null;

                            // Restart recording for this player
                            if (bridgeConfig.isReplayAutoRecord()) {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    Player player = Bukkit.getPlayer(targetUuid);
                                    if (player != null && player.isOnline()) {
                                        startRecordingForPlayer(player);
                                    }
                                }, 40L);
                            }

                            if (replayFile == null || !replayFile.exists()) {
                                logger.warning("[bridge] No replay file found for " + targetName + " after stopping recording");
                                return CompletableFuture.completedFuture(null);
                            }

                            // Upload to modl-backend
                            return uploader.uploadAsync(replayFile, recordingConfig.mcVersion())
                                    .thenApply(replayId -> {
                                        logger.info("[bridge] Replay uploaded for " + targetName + ": " + replayId);
                                        return replayId;
                                    })
                                    .whenComplete((replayId, ex) -> {
                                        if (ex != null) {
                                            logger.warning("[bridge] Replay upload failed for " + targetName + ": " + ex.getMessage());
                                        }
                                        replayFile.delete();
                                    });
                        });
            }

            @Override
            public boolean isReplayAvailable(UUID playerUuid) {
                return recordingManager.isRecording(playerUuid);
            }
        };

        // Auto-start recording for all online players
        if (bridgeConfig.isReplayAutoRecord()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                startRecordingForPlayer(player);
            }
        }

        logger.info("[bridge] Replay recording initialized (auto-record: " + bridgeConfig.isReplayAutoRecord() + ")");
    }

    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String host = uri.getHost();
            return host != null ? host : url.trim();
        } catch (Exception e) {
            String result = normalized.substring(normalized.indexOf("://") + 3);
            int slashIndex = result.indexOf('/');
            if (slashIndex > 0) result = result.substring(0, slashIndex);
            return result;
        }
    }

    private void startRecordingForPlayer(Player player) {
        if (recordingManager == null || packetRecorder == null) return;
        if (recordingManager.isRecording(player.getUniqueId())) return;

        Location loc = player.getLocation();
        int radius = bridgeConfig.getReplayRadius();

        // Seed the target player into their own EntityTracker (they never receive SPAWN_ENTITY for themselves)
        packetRecorder.trackSelf(player.getUniqueId(), player.getName(), player.getEntityId(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        // Seed nearby players so they're available at stop time
        // even if SPAWN_ENTITY packets arrived before PacketRecorder was registered
        double radiusSq = (double) radius * radius;
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.equals(player)) continue;
            if (!nearby.getWorld().equals(loc.getWorld())) continue;
            if (nearby.getLocation().distanceSquared(loc) > radiusSq) continue;
            Location nLoc = nearby.getLocation();
            packetRecorder.getEntityTracker().trackPlayer(
                    player.getUniqueId(), nearby.getEntityId(), nearby.getUniqueId(), nearby.getName(),
                    nLoc.getX(), nLoc.getY(), nLoc.getZ(),
                    nLoc.getYaw(), nLoc.getPitch());
        }

        recordingManager.startRecording(
                player.getUniqueId(), player.getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );

        // Seed inventory cache from Bukkit API so equipment/inventory is available
        // even if no WINDOW_ITEMS packet has been intercepted yet
        seedInventoryFromBukkit(player);
        // Emit initial equipment from cached inventory state
        packetRecorder.emitInitialSelfEquipment(player.getUniqueId());
    }

    /**
     * Seed the PacketRecorder's inventory cache from Bukkit API.
     * Maps Bukkit inventory contents to protocol slot indices so equipment/inventory
     * is available immediately when recording starts, without waiting for WINDOW_ITEMS.
     */
    private void seedInventoryFromBukkit(Player player) {
        PlayerInventory inv = player.getInventory();
        Map<Integer, String> protocolSlots = new HashMap<>();
        Map<Integer, Integer> protocolCounts = new HashMap<>();

        // Protocol slot layout for window 0 (player inventory):
        // 5-8: armor (5=helmet, 6=chestplate, 7=leggings, 8=boots)
        // 9-35: main inventory
        // 36-44: hotbar
        // 45: offhand

        // Bukkit getContents() returns: 0-8=hotbar, 9-35=main inv, 36-39=armor (boots,legs,chest,helm), 40=offhand
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && i <= 40; i++) {
            ItemStack item = contents[i];
            String name = (item != null && item.getType() != Material.AIR)
                    ? item.getType().name().toLowerCase() : "air";
            int amount = (item != null && item.getType() != Material.AIR)
                    ? item.getAmount() : 0;
            int protocolSlot;
            if (i >= 0 && i <= 8) {
                protocolSlot = 36 + i; // hotbar → protocol 36-44
            } else if (i >= 9 && i <= 35) {
                protocolSlot = i; // main inv → same
            } else if (i == 36) {
                protocolSlot = 8; // boots
            } else if (i == 37) {
                protocolSlot = 7; // leggings
            } else if (i == 38) {
                protocolSlot = 6; // chestplate
            } else if (i == 39) {
                protocolSlot = 5; // helmet
            } else if (i == 40) {
                protocolSlot = 45; // offhand
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
            // Delay slightly to allow chunk data to arrive
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    startRecordingForPlayer(event.getPlayer());
                }
            }, 40L); // 2 seconds
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (violationTracker != null) violationTracker.resetPlayer(playerId);
        if (autoReporter != null) autoReporter.clearCooldown(playerId);

        // Stop recording async (drain + flush on writer thread, fire-and-forget)
        if (recordingManager != null && recordingManager.isRecording(playerId)) {
            recordingManager.stopRecordingAsync(playerId);
        }
        if (packetRecorder != null) {
            packetRecorder.disconnectPlayer(playerId);
        }
    }
}
