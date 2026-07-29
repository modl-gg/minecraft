package gg.modl.minecraft.spigot.bridge.folia;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FoliaScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private final JavaPlugin plugin;

    private volatile boolean initialized;
    private Method getGlobalRegionSchedulerMethod;
    private Method globalRunMethod;
    private Method getAsyncSchedulerMethod;
    private Method asyncRunAtFixedRateMethod;
    private Method entityGetSchedulerMethod;
    private volatile Method entityRunMethod;
    private volatile Method entityExecuteDelayedMethod;
    private volatile Method scheduledTaskCancelMethod;

    public FoliaScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    private void init() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            try {
                getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                globalRunMethod = globalScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);

                getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                asyncRunAtFixedRateMethod = asyncScheduler.getClass().getMethod("runAtFixedRate",
                        Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                entityGetSchedulerMethod = Entity.class.getMethod("getScheduler");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("[bridge] Folia scheduler API unavailable", e);
            }
            initialized = true;
        }
    }

    public void runGlobal(Runnable task) {
        init();
        try {
            Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            globalRunMethod.invoke(globalScheduler, plugin, consumer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[bridge] Failed to run global Folia task", e);
        }
    }

    public void runForEntity(Entity entity, Runnable task) {
        init();
        try {
            Object entityScheduler = entityGetSchedulerMethod.invoke(entity);
            if (entityRunMethod == null) {
                entityRunMethod = entityScheduler.getClass().getMethod("run",
                        Plugin.class, Consumer.class, Runnable.class);
            }
            Consumer<Object> consumer = scheduledTask -> task.run();
            entityRunMethod.invoke(entityScheduler, plugin, consumer, (Runnable) null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[bridge] Failed to run entity Folia task", e);
        }
    }

    public void runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        init();
        try {
            Object entityScheduler = entityGetSchedulerMethod.invoke(entity);
            if (entityExecuteDelayedMethod == null) {
                entityExecuteDelayedMethod = entityScheduler.getClass().getMethod("execute",
                        Plugin.class, Runnable.class, Runnable.class, long.class);
            }
            entityExecuteDelayedMethod.invoke(entityScheduler, plugin, task, (Runnable) null, delayTicks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[bridge] Failed to run delayed entity Folia task", e);
        }
    }

    public Object runAsyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        init();
        try {
            Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            return asyncRunAtFixedRateMethod.invoke(asyncScheduler, plugin, consumer, delay, period, unit);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[bridge] Failed to run async Folia timer", e);
        }
    }

    public void cancelFoliaTask(Object scheduledTask) {
        if (scheduledTask == null) return;
        try {
            if (scheduledTaskCancelMethod == null) {
                scheduledTaskCancelMethod = scheduledTask.getClass().getMethod("cancel");
            }
            scheduledTaskCancelMethod.invoke(scheduledTask);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "[bridge] Failed to cancel Folia task", e);
        }
    }
}
