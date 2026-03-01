package gg.modl.minecraft.spigot;

import gg.modl.minecraft.core.sync.StatWipeExecutor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class SpigotStatWipeExecutor implements StatWipeExecutor {
    private final Logger logger;
    private final boolean debugMode;

    public SpigotStatWipeExecutor(Logger logger, boolean debugMode) {
        this.logger = logger;
        this.debugMode = debugMode;
    }

    @Override
    public void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback) {
        Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("modl-bridge");
        if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
            logger.warning("[StatWipe] modl-bridge plugin not found or not enabled - stat wipe for " +
                    username + " will retry on next sync");
            // Do NOT call callback with success - backend will retry on next sync
            return;
        }

        try {
            Method method = bridgePlugin.getClass().getMethod("executeStatWipeCommands", String.class, String.class);
            boolean success = (boolean) method.invoke(bridgePlugin, username, punishmentId);
            String serverName = Bukkit.getServer().getName();
            callback.onComplete(success, serverName);
        } catch (NoSuchMethodException e) {
            logger.warning("[StatWipe] modl-bridge plugin does not support stat wipe commands (outdated version?)");
        } catch (Exception e) {
            logger.severe("[StatWipe] Failed to execute stat wipe via bridge plugin: " + e.getMessage());
            if (debugMode) {
                e.printStackTrace();
            }
        }
    }
}
