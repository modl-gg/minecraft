package gg.modl.minecraft.core.util;

import java.util.logging.Logger;

/**
 * Simple logging abstraction so core code doesn't depend on a specific logging framework.
 * Platforms wrap their native logger (JUL for Spigot/Bungee, SLF4J for Velocity).
 */
public interface PluginLogger {
    void info(String message);
    void warning(String message);
    void severe(String message);
    default void debug(String message) {}

    /** Wraps a {@link java.util.logging.Logger}. */
    static PluginLogger fromJul(Logger logger) {
        return new PluginLogger() {
            @Override public void info(String message) { logger.info(message); }
            @Override public void warning(String message) { logger.warning(message); }
            @Override public void severe(String message) { logger.severe(message); }
            @Override public void debug(String message) { logger.fine(message); }
        };
    }
}
