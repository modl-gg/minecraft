package gg.modl.minecraft.core.util;

import java.util.logging.Logger;

public interface PluginLogger {
    void info(String message);
    void warning(String message);
    void severe(String message);
    default void debug(String message) {}

    static PluginLogger fromJul(Logger logger) {
        return new PluginLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warning(String message) { logger.warning(message); }
            @Override public void severe(String message) { logger.severe(message); }
            @Override public void debug(String message) { logger.fine(message); }
        };
    }
}
