package gg.modl.minecraft.spigot;

import gg.modl.minecraft.core.service.sync.StatWipeExecutor;
import gg.modl.minecraft.core.util.PluginLogger;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

@RequiredArgsConstructor
public class SpigotStatWipeExecutor implements StatWipeExecutor {
    private static final String BRIDGE_PLUGIN_NAME = "modl-bridge", EXECUTE_METHOD = "executeStatWipeCommands";

    private final PluginLogger logger;
    private final boolean debugMode;

    private volatile Method cachedMethod;
    private volatile boolean methodResolved;

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME);
        if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
            // don't call callback, backend will retry on next sync
            logger.warning("[bridge] modl-bridge plugin not found or not enabled - stat wipe for " +
                    username + " will retry on next sync");
            return;
        }

        try {
            Method method = resolveMethod(bridgePlugin);
            if (method == null) {
                logger.warning("[bridge] modl-bridge plugin does not support stat wipe commands (outdated version?)");
                return;
            }
            boolean success = (boolean) method.invoke(bridgePlugin, username, punishmentId);
            callback.onComplete(success, Bukkit.getServer().getName());
        } catch (Exception e) {
            logger.severe("[bridge] Failed to execute stat wipe via bridge plugin: " + e.getMessage());
            if (debugMode) logger.severe("[bridge] Stack trace: " + stackTraceToString(e));
        }
    }

    private Method resolveMethod(Plugin bridgePlugin) {
        if (methodResolved) return cachedMethod;
        synchronized (this) {
            if (methodResolved) return cachedMethod;
            try {
                cachedMethod = bridgePlugin.getClass().getMethod(EXECUTE_METHOD, String.class, String.class);
            } catch (NoSuchMethodException e) {
                cachedMethod = null;
            }
            methodResolved = true;
            return cachedMethod;
        }
    }

    private static String stackTraceToString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
