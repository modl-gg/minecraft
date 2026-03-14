package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.spigot.bridge.command.ProxyCmdCommand;
import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import gg.modl.minecraft.spigot.bridge.config.StaffModeConfig;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import gg.modl.minecraft.spigot.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.spigot.bridge.query.BridgeQueryServer;
import gg.modl.minecraft.spigot.bridge.reporter.AutoReporter;
import gg.modl.minecraft.spigot.bridge.reporter.TicketCreator;
import gg.modl.minecraft.spigot.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.spigot.bridge.reporter.hook.AntiCheatHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.GrimHook;
import gg.modl.minecraft.spigot.bridge.reporter.hook.PolarHook;
import gg.modl.minecraft.spigot.bridge.statwipe.StatWipeHandler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BridgeComponent implements Listener {
    private final JavaPlugin plugin;
    private final String apiKey;
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

    public BridgeComponent(JavaPlugin plugin, String apiKey, PluginLogger logger) {
        this.plugin = plugin;
        this.apiKey = apiKey;
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
        // Save default bridge-config.yml if not present
        if (!BridgeConfig.exists(plugin.getDataFolder().toPath())) {
            plugin.saveResource("bridge-config.yml", false);
        }

        try {
            bridgeConfig = BridgeConfig.load(plugin.getDataFolder().toPath());
        } catch (IOException e) {
            logger.severe("[bridge] Failed to load bridge-config.yml: " + e.getMessage());
            bridgeConfig = new BridgeConfig();
        }
        bridgeConfig.setApiKey(apiKey);

        BridgeLocaleManager localeManager = new BridgeLocaleManager(plugin.getLogger());

        // Save default staff_mode.yml if not present
        if (!plugin.getDataFolder().toPath().resolve("staff_mode.yml").toFile().exists()) {
            plugin.saveResource("staff_mode.yml", false);
        }

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

        initializeReplayService();
        registerAntiCheatHooks();

        if (queryServer != null) {
            plugin.getCommand("proxycmd").setExecutor(new ProxyCmdCommand(plugin, localeManager, queryServer));
        }

        logger.info("[bridge] Enabled with " + hooks.size() + " anticheat hook(s)"
                + (polarAvailable ? " (Polar pending callback)" : ""));
    }

    public void disable() {
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
     * Detects ModlBridge plugin and creates a reflection-based ReplayService
     * that delegates to its BridgeReplayService.
     */
    private void initializeReplayService() {
        try {
            org.bukkit.plugin.Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("ModlBridge");
            if (bridgePlugin == null) {
                logger.info("[bridge] ModlBridge plugin not found, replay capture unavailable");
                return;
            }

            // Get the BridgeReplayService field from ModlBridgePlugin
            Method getReplayServiceMethod = bridgePlugin.getClass().getMethod("getReplayService");
            Object bridgeReplayService = getReplayServiceMethod.invoke(bridgePlugin);
            if (bridgeReplayService == null) {
                logger.info("[bridge] ModlBridge replay service not initialized, replay capture unavailable");
                return;
            }

            // Cache reflection methods
            Method captureMethod = bridgeReplayService.getClass().getMethod("captureAndUploadReplay", UUID.class, String.class);
            Method isAvailableMethod = bridgeReplayService.getClass().getMethod("isReplayAvailable", UUID.class);

            this.replayService = new ReplayService() {
                @Override
                @SuppressWarnings("unchecked")
                public CompletableFuture<String> captureReplay(UUID targetUuid, String targetName) {
                    try {
                        return (CompletableFuture<String>) captureMethod.invoke(bridgeReplayService, targetUuid, targetName);
                    } catch (Exception e) {
                        logger.warning("[bridge] Failed to capture replay: " + e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                }

                @Override
                public boolean isReplayAvailable(UUID playerUuid) {
                    try {
                        return (boolean) isAvailableMethod.invoke(bridgeReplayService, playerUuid);
                    } catch (Exception e) {
                        return false;
                    }
                }
            };

            logger.info("[bridge] Replay capture enabled via ModlBridge plugin");
        } catch (Exception e) {
            logger.info("[bridge] Could not initialize replay service: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (violationTracker != null) violationTracker.resetPlayer(playerId);
        if (autoReporter != null) autoReporter.clearCooldown(playerId);
    }
}
