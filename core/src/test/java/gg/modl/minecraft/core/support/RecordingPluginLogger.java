package gg.modl.minecraft.core.support;

import gg.modl.minecraft.core.util.PluginLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingPluginLogger implements PluginLogger {
    private final List<String> infos = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> severes = new CopyOnWriteArrayList<>();
    private final List<String> debugs = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        infos.add(message);
    }

    @Override
    public void warning(String message) {
        warnings.add(message);
    }

    @Override
    public void severe(String message) {
        severes.add(message);
    }

    @Override
    public void debug(String message) {
        debugs.add(message);
    }

    public List<String> infos() {
        return infos;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> severes() {
        return severes;
    }

    public List<String> debugs() {
        return debugs;
    }
}
