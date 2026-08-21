package gg.modl.minecraft.spigot.bridge.folia;

import gg.modl.minecraft.core.util.PluginLogger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class AsyncTeleporter {
    private static final Method TELEPORT_ASYNC = resolveTeleportAsync();

    private final PluginLogger logger;

    public AsyncTeleporter(PluginLogger logger) {
        this.logger = logger;
    }

    public void teleport(Entity entity, Location destination) {
        if (TELEPORT_ASYNC == null) {
            entity.teleport(destination);
            return;
        }
        try {
            Object pending = TELEPORT_ASYNC.invoke(entity, destination);
            if (pending instanceof CompletableFuture) {
                ((CompletableFuture<?>) pending).whenComplete((result, failure) -> {
                    if (failure != null) {
                        logger.warning("[bridge] Failed to teleport " + entity.getName(), failure);
                    } else if (Boolean.FALSE.equals(result)) {
                        logger.warning("[bridge] Teleport of " + entity.getName() + " was refused");
                    }
                });
            }
        } catch (ReflectiveOperationException e) {
            logger.warning("[bridge] Failed to teleport " + entity.getName(), e);
        }
    }

    private static Method resolveTeleportAsync() {
        try {
            return Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
