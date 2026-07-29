package gg.modl.minecraft.spigot.boot;

import gg.modl.minecraft.core.boot.BootConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class SignedVelocityBootstrap {
    private static final String SIGNED_VELOCITY_CLASS = "io.github._4drian3d.signedvelocity.paper.SignedVelocity";
    private static final String PAPER_ASYNC_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";

    private final JavaPlugin plugin;

    public SignedVelocityBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init(BootConfig bootConfig) {
        if (bootConfig.getMode() != BootConfig.Mode.BRIDGE_ONLY) return;
        String proxyType = bootConfig.getProxyType();
        if (proxyType != null && !"velocity".equalsIgnoreCase(proxyType)) return;
        if (plugin.getServer().getPluginManager().getPlugin("SignedVelocity") != null) {
            plugin.getLogger().info("[SignedVelocity] Using standalone SignedVelocity plugin");
            return;
        }

        try {
            Class.forName(PAPER_ASYNC_CHAT_EVENT);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[SignedVelocity] Paper API not available - signed chat enforcement disabled");
            return;
        }

        try {
            Class<?> svClass = Class.forName(SIGNED_VELOCITY_CLASS);
            Method initMethod = svClass.getMethod("init", JavaPlugin.class, Logger.class);
            Method getSlf4j = plugin.getClass().getMethod("getSLF4JLogger");
            Object slf4jLogger = getSlf4j.invoke(plugin);
            initMethod.invoke(null, plugin, slf4jLogger);
            plugin.getLogger().info("[SignedVelocity] Embedded listeners registered");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "[SignedVelocity] Failed to initialize", e);
        }
    }

    public void shutdown() {
        try {
            Class<?> svClass = Class.forName(SIGNED_VELOCITY_CLASS);
            Method shutdownMethod = svClass.getMethod("shutdown");
            shutdownMethod.invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "[SignedVelocity] Failed to shutdown", e);
        }
    }
}
