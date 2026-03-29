package gg.modl.minecraft.spigot.bridge.folia;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Reflection-based helper for Folia's region-based scheduling APIs.
 * Uses reflection because the spigot module compiles against Spigot 1.8.8 API
 * which does not have these methods.
 */
public final class FoliaSchedulerHelper {

    private static Method getGlobalRegionSchedulerMethod;
    private static Method globalRunMethod;
    private static Method getAsyncSchedulerMethod;
    private static Method asyncRunAtFixedRateMethod;
    private static Method entityGetSchedulerMethod;
    private static Method entityRunMethod;
    private static Method entityRunDelayedMethod;
    private static Method scheduledTaskCancelMethod;

    private static volatile boolean initialized = false;
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

    private FoliaSchedulerHelper() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    private static void init() {
        if (initialized) return;
        synchronized (FoliaSchedulerHelper.class) {
            if (initialized) return;
            try {
                getGlobalRegionSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
                globalRunMethod = globalScheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);

                getAsyncSchedulerMethod = Bukkit.class.getMethod("getAsyncScheduler");
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
                asyncRunAtFixedRateMethod = asyncScheduler.getClass().getMethod("runAtFixedRate",
                        org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                entityGetSchedulerMethod = Entity.class.getMethod("getScheduler");
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[bridge] Failed to initialize Folia scheduler helpers", e);
            }
            initialized = true;
        }
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        init();
        try {
            Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(null);
            Consumer<Object> consumer = (scheduledTask) -> task.run();
            globalRunMethod.invoke(globalScheduler, plugin, consumer);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[bridge] Failed to run global Folia task", e);
            task.run();
        }
    }

    public static void runForEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        init();
        try {
            Object entityScheduler = entityGetSchedulerMethod.invoke(entity);
            if (entityRunMethod == null) {
                entityRunMethod = entityScheduler.getClass().getMethod("run",
                        org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
            }
            Consumer<Object> consumer = (scheduledTask) -> task.run();
            entityRunMethod.invoke(entityScheduler, plugin, consumer, (Runnable) null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[bridge] Failed to run entity Folia task", e);
            task.run();
        }
    }

    public static void runForEntityLater(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        init();
        try {
            Object entityScheduler = entityGetSchedulerMethod.invoke(entity);
            if (entityRunDelayedMethod == null) {
                entityRunDelayedMethod = entityScheduler.getClass().getMethod("execute",
                        org.bukkit.plugin.Plugin.class, Runnable.class, Runnable.class, long.class);
            }
            entityRunDelayedMethod.invoke(entityScheduler, plugin, task, (Runnable) null, delayTicks);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[bridge] Failed to run delayed entity Folia task", e);
            task.run();
        }
    }

    public static Object runAsyncTimer(JavaPlugin plugin, Runnable task, long delay, long period, TimeUnit unit) {
        init();
        try {
            Object asyncScheduler = getAsyncSchedulerMethod.invoke(null);
            Consumer<Object> consumer = (scheduledTask) -> task.run();
            return asyncRunAtFixedRateMethod.invoke(asyncScheduler, plugin, consumer, delay, period, unit);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[bridge] Failed to run async Folia timer", e);
            return null;
        }
    }

    public static void cancelFoliaTask(Object scheduledTask) {
        if (scheduledTask == null) return;
        try {
            if (scheduledTaskCancelMethod == null) {
                scheduledTaskCancelMethod = scheduledTask.getClass().getMethod("cancel");
            }
            scheduledTaskCancelMethod.invoke(scheduledTask);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[bridge] Failed to cancel Folia task", e);
        }
    }
}
