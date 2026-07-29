package gg.modl.minecraft.spigot.bridge.reporter.hook;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import me.frep.vulcan.api.event.VulcanFlagEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class VulcanHook extends AbstractAnticheatHook<VulcanFlagEvent> implements Listener {
    private static final String HOOK_NAME = "Vulcan";

    public VulcanHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker, AutoReporter autoReporter) {
        super(plugin, config, violationTracker, autoReporter, HOOK_NAME, HOOK_NAME, DetectionSource.VULCAN);
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin(HOOK_NAME) != null;
    }

    @Override
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        logHooked();
    }

    @Override
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onVulcanFlag(VulcanFlagEvent event) {
        handle(event);
    }

    @Override
    protected AnticheatFlag extractFlag(VulcanFlagEvent event) {
        if (event.isCancelled()) {
            logCancelled(event.getCheck().getName(), event.getPlayer().getName());
            return null;
        }

        return new AnticheatFlag(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getCheck().getName(), event.getInfo());
    }
}
