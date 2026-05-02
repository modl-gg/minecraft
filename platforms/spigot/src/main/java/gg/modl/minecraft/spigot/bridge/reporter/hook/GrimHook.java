package gg.modl.minecraft.spigot.bridge.reporter.hook;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.BasicGrimPlugin;
import ac.grim.grimac.api.plugin.GrimPlugin;
import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import static gg.modl.minecraft.core.util.Java8Collections.listOf;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class GrimHook implements AntiCheatHook {
    private static final String HOOK_NAME = "GrimAC";

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ViolationTracker violationTracker;
    private final AutoReporter autoReporter;

    @Override
    public String getName() {
        return HOOK_NAME;
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin(HOOK_NAME) != null;
    }

    @Override
    public void register() {
        try {
            GrimAbstractAPI grimApi = Bukkit.getServicesManager().getRegistration(GrimAbstractAPI.class).getProvider();
            GrimPlugin grimPlugin = new BasicGrimPlugin(
                    plugin.getLogger(),
                    plugin.getDataFolder(),
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    listOf()
            );
            grimApi.getEventBus().subscribe(grimPlugin, FlagEvent.class, this::onFlag);
            plugin.getLogger().info("Hooked into " + HOOK_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into " + HOOK_NAME + ": " + e.getMessage());
        }
    }

    @Override
    public void unregister() {
    }

    private void onFlag(FlagEvent event) {
        try {
            if (event.isCancelled()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Grim FlagEvent cancelled for check: "
                            + event.getCheck().getCheckName() + " player: " + event.getPlayer().getName());
                }
                return;
            }

            GrimUser user = event.getPlayer();
            UUID uuid = user.getUniqueId();
            String playerName = user.getName();
            String checkName = event.getCheck().getCheckName();
            String verbose = event.getVerbose();

            logDebugFlag(playerName, uuid, checkName, verbose);
            violationTracker.addViolation(uuid, DetectionSource.GRIM, checkName, verbose);
            autoReporter.checkAndReport(uuid, playerName, DetectionSource.GRIM, checkName);
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing Grim flag event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logDebugFlag(String playerName, UUID uuid, String checkName, String verbose) {
        if (!config.isDebug()) return;
        int currentCount = violationTracker.getViolationCount(uuid, DetectionSource.GRIM, checkName);
        plugin.getLogger().info("[DEBUG] Grim flag: player=" + playerName
                + " check=" + checkName + " currentVL=" + (currentCount + 1)
                + " threshold=" + config.getReportViolationThreshold(checkName)
                + " verbose=" + verbose);
    }
}
