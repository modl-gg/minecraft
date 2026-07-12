package gg.modl.minecraft.spigot.bridge.reporter.hook;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.bridge.reporter.AutoReporter;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.event.listener.RegisteredListener;
import top.polar.api.event.listener.repository.EventListenerRepository;
import top.polar.api.user.event.DetectionAlertEvent;

public class PolarHook extends AbstractAnticheatHook<DetectionAlertEvent> {
    private static final String HOOK_NAME = "Polar";
    private static final String POLAR_API_CLASS = "top.polar.api.PolarApiAccessor";

    private EventListenerRepository repository;
    private RegisteredListener<DetectionAlertEvent> registeredListener;

    public PolarHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker, AutoReporter autoReporter) {
        super(plugin, config, violationTracker, autoReporter, HOOK_NAME, HOOK_NAME, DetectionSource.POLAR);
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
            repository = polarApi.events().repository();
            registeredListener = repository.registerListener(DetectionAlertEvent.class, this::handle);
            logHooked();
        } catch (Exception e) {
            logHookFailure(e);
        }
    }

    @Override
    public void unregister() {
        if (repository != null && registeredListener != null) {
            repository.unregisterListener(registeredListener);
        }
    }

    public boolean isRegistered() {
        return registeredListener != null;
    }

    @Override
    protected String eventNoun() {
        return "detection";
    }

    @Override
    protected String verboseLabel() {
        return "details";
    }

    @Override
    protected AnticheatFlag extractFlag(DetectionAlertEvent event) {
        return new AnticheatFlag(event.user().uuid(), event.user().username(),
                event.check().type().name(), event.details());
    }
}
