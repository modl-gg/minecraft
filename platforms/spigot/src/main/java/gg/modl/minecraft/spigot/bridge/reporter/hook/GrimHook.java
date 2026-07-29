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
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import static gg.modl.minecraft.core.util.Java8Collections.listOf;

public class GrimHook extends AbstractAnticheatHook<FlagEvent> {
    private static final String HOOK_NAME = "GrimAC";
    private static final String LOG_LABEL = "Grim";

    private GrimAbstractAPI grimApi;
    private GrimPlugin grimPlugin;

    public GrimHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker, AutoReporter autoReporter) {
        super(plugin, config, violationTracker, autoReporter, HOOK_NAME, LOG_LABEL, DetectionSource.GRIM);
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin(HOOK_NAME) != null;
    }

    @Override
    public void register() {
        try {
            grimApi = Bukkit.getServicesManager().getRegistration(GrimAbstractAPI.class).getProvider();
            grimPlugin = new BasicGrimPlugin(
                    plugin.getLogger(),
                    plugin.getDataFolder(),
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    listOf()
            );
            grimApi.getEventBus().subscribe(grimPlugin, FlagEvent.class, this::handle);
            logHooked();
        } catch (Exception e) {
            logHookFailure(e);
        }
    }

    @Override
    public void unregister() {
        if (grimApi != null && grimPlugin != null) {
            grimApi.getEventBus().unregisterAllListeners(grimPlugin);
        }
    }

    @Override
    protected AnticheatFlag extractFlag(FlagEvent event) {
        if (event.isCancelled()) {
            logCancelled(event.getCheck().getCheckName(), event.getPlayer().getName());
            return null;
        }

        GrimUser user = event.getPlayer();
        return new AnticheatFlag(user.getUniqueId(), user.getName(),
                event.getCheck().getCheckName(), event.getVerbose());
    }
}
