package gg.modl.minecraft.spigot.bridge.reporter.hook;

import gg.modl.minecraft.spigot.bridge.config.BridgeConfig;
import gg.modl.minecraft.spigot.bridge.reporter.AutoReporter;
import gg.modl.minecraft.spigot.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.spigot.bridge.reporter.detection.ViolationTracker;
import lombok.RequiredArgsConstructor;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

@RequiredArgsConstructor
public class VulcanHook implements AntiCheatHook, Listener {
    private static final String HOOK_NAME = "Vulcan";

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
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Hooked into " + HOOK_NAME);
    }

    @Override
    public void unregister() {
    }

    @EventHandler
    public void onVulcanFlag(VulcanFlagEvent event) {
        try {
            if (event.isCancelled()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Vulcan FlagEvent cancelled for check: "
                            + event.getCheck().getName() + " player: " + event.getPlayer().getName());
                }
                return;
            }

            UUID uuid = event.getPlayer().getUniqueId();
            String playerName = event.getPlayer().getName();
            String checkName = event.getCheck().getName() + " (" + event.getCheck().getType() + ")";
            String verbose = event.getInfo();

            logDebugFlag(playerName, uuid, checkName, verbose);
            violationTracker.addViolation(uuid, DetectionSource.VULCAN, checkName, verbose);
            autoReporter.checkAndReport(uuid, playerName, DetectionSource.VULCAN, checkName);
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing Vulcan flag event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logDebugFlag(String playerName, UUID uuid, String checkName, String verbose) {
        if (!config.isDebug()) return;
        int currentCount = violationTracker.getViolationCount(uuid, DetectionSource.VULCAN, checkName);
        plugin.getLogger().info("[DEBUG] Vulcan flag: player=" + playerName
                + " check=" + checkName + " currentVL=" + (currentCount + 1)
                + " threshold=" + config.getReportViolationThreshold(checkName)
                + " verbose=" + verbose);
    }
}
