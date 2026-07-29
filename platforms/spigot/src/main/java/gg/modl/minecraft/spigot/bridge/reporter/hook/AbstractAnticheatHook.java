package gg.modl.minecraft.spigot.bridge.reporter.hook;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.hook.AntiCheatHook;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class AbstractAnticheatHook<E> implements AntiCheatHook {

    protected final JavaPlugin plugin;
    protected final BridgeConfig config;
    protected final ViolationTracker violationTracker;
    protected final AutoReporter autoReporter;
    private final String hookName;
    private final String logLabel;
    private final DetectionSource source;

    protected AbstractAnticheatHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker,
                                    AutoReporter autoReporter, String hookName, String logLabel, DetectionSource source) {
        this.plugin = plugin;
        this.config = config;
        this.violationTracker = violationTracker;
        this.autoReporter = autoReporter;
        this.hookName = hookName;
        this.logLabel = logLabel;
        this.source = source;
    }

    @Override
    public String getName() {
        return hookName;
    }

    protected abstract AnticheatFlag extractFlag(E event);

    protected final void handle(E event) {
        try {
            AnticheatFlag flag = extractFlag(event);
            if (flag == null) return;

            logDebugFlag(flag);
            violationTracker.addViolation(flag.getUuid(), source, flag.getCheckName(), flag.getVerbose());
            autoReporter.checkAndReport(flag.getUuid(), flag.getPlayerName(), source, flag.getCheckName());
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing " + logLabel + " " + eventNoun() + " event: " + e.getMessage());
        }
    }

    protected String eventNoun() {
        return "flag";
    }

    protected String verboseLabel() {
        return "verbose";
    }

    protected void logHooked() {
        plugin.getLogger().info("Hooked into " + hookName);
    }

    protected void logHookFailure(Exception e) {
        plugin.getLogger().warning("Failed to hook into " + hookName + ": " + e.getMessage());
    }

    protected void logCancelled(String checkName, String playerName) {
        if (!config.isDebug()) return;
        plugin.getLogger().info("[DEBUG] " + logLabel + " FlagEvent cancelled for check: "
                + checkName + " player: " + playerName);
    }

    private void logDebugFlag(AnticheatFlag flag) {
        if (!config.isDebug()) return;
        int currentCount = violationTracker.getViolationCount(flag.getUuid(), source, flag.getCheckName());
        plugin.getLogger().info("[DEBUG] " + logLabel + " " + eventNoun() + ": player=" + flag.getPlayerName()
                + " check=" + flag.getCheckName() + " currentVL=" + (currentCount + 1)
                + " threshold=" + config.getReportViolationThreshold(flag.getCheckName())
                + " " + verboseLabel() + "=" + flag.getVerbose());
    }
}
