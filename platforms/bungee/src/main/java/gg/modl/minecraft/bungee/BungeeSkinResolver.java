package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.util.PluginLogger;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.lang.reflect.Method;

final class BungeeSkinResolver {
    private final PluginLogger logger;

    private volatile boolean resolved = false;
    private volatile Method getLoginProfileMethod;
    private volatile Method getPropertiesMethod;
    private volatile Method getNameMethod;
    private volatile Method getValueMethod;

    BungeeSkinResolver(PluginLogger logger) {
        this.logger = logger;
    }

    String resolveTexture(ProxiedPlayer player) {
        try {
            if (!resolved) resolveMethods(player);
            if (getLoginProfileMethod == null) return null;

            Object pendingConnection = player.getPendingConnection();
            Object profile = getLoginProfileMethod.invoke(pendingConnection);
            if (profile == null) return null;
            Object[] properties = (Object[]) getPropertiesMethod.invoke(profile);
            if (properties == null) return null;
            for (Object prop : properties) {
                String name = (String) getNameMethod.invoke(prop);
                if ("textures".equals(name)) return (String) getValueMethod.invoke(prop);
            }
        } catch (Exception e) {
            logger.debug("Skin texture lookup failed: " + e.getMessage());
        }
        return null;
    }

    private synchronized void resolveMethods(ProxiedPlayer player) {
        if (resolved) return;
        try {
            Object pendingConnection = player.getPendingConnection();
            getLoginProfileMethod = pendingConnection.getClass().getMethod("getLoginProfile");
            Object profile = getLoginProfileMethod.invoke(pendingConnection);
            if (profile != null) {
                getPropertiesMethod = profile.getClass().getMethod("getProperties");
                Object[] properties = (Object[]) getPropertiesMethod.invoke(profile);
                if (properties != null) {
                    for (Object prop : properties) {
                        getNameMethod = prop.getClass().getMethod("getName");
                        getValueMethod = prop.getClass().getMethod("getValue");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve skin reflection methods: " + e.getMessage());
            getLoginProfileMethod = null;
        }
        resolved = true;
    }
}
