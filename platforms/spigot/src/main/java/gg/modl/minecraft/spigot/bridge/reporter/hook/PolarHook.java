package gg.modl.minecraft.spigot.bridge.reporter.hook;

import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import gg.modl.minecraft.spigot.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.spigot.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.spigot.bridge.reporter.AutoReporter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.user.event.DetectionAlertEvent;

import java.util.UUID;

@RequiredArgsConstructor
public class PolarHook implements AntiCheatHook {
    private static final String HOOK_NAME = "Polar";
    private static final String POLAR_API_CLASS = "top.polar.api.PolarApiAccessor";

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
        try {
            Class.forName(POLAR_API_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void register() {
        try {
            PolarApi polarApi = PolarApiAccessor.access().get();
            if (polarApi == null) {
                plugin.getLogger().warning("Polar API reference was null");
                return;
            }
            polarApi.events().repository().registerListener(DetectionAlertEvent.class, this::onDetection);
            plugin.getLogger().info("Hooked into " + HOOK_NAME);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into " + HOOK_NAME + ": " + e.getMessage());
        }
    }

    @Override
    public void unregister() {
        // polar event system does not require explicit unregistration
    }

    private void onDetection(DetectionAlertEvent event) {
        try {
            UUID uuid = event.user().uuid();
            String playerName = event.user().username();
            String checkName = event.check().type().name();
            String verbose = event.details();

            logDebugDetection(HOOK_NAME, playerName, uuid, checkName, verbose);
            violationTracker.addViolation(uuid, DetectionSource.POLAR, checkName, verbose);
            autoReporter.checkAndReport(uuid, playerName, DetectionSource.POLAR, checkName);
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing Polar detection event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logDebugDetection(String sourceName, String playerName, UUID uuid, String checkName, String verbose) {
        if (!config.isDebug()) return;
        int currentCount = violationTracker.getViolationCount(uuid, DetectionSource.POLAR, checkName);
        plugin.getLogger().info("[DEBUG] " + sourceName + " detection: player=" + playerName
                + " check=" + checkName + " currentVL=" + (currentCount + 1)
                + " threshold=" + config.getReportViolationThreshold(checkName)
                + " details=" + verbose);
    }
}
