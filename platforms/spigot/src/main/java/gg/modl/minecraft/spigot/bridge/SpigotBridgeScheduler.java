package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.spigot.bridge.folia.FoliaScheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.bukkit.scheduler.BukkitTask;

public class SpigotBridgeScheduler implements BridgeScheduler {
    private final JavaPlugin plugin;
    @Getter private final boolean folia;
    private final FoliaScheduler foliaScheduler;
    private final ScheduledExecutorService delayExecutor;

    public SpigotBridgeScheduler(JavaPlugin plugin) {
        this(plugin, FoliaScheduler.isFolia(), null);
    }

    SpigotBridgeScheduler(JavaPlugin plugin, boolean folia, ScheduledExecutorService delayExecutor) {
        this.plugin = plugin;
        this.folia = folia;
        this.foliaScheduler = new FoliaScheduler(plugin);
        if (!folia) {
            this.delayExecutor = null;
        } else {
            this.delayExecutor = delayExecutor != null ? delayExecutor : createDelayExecutor();
        }
    }

    private static ScheduledExecutorService createDelayExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-bridge-delay");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void runOnMainThread(Runnable task) {
        if (folia) {
            foliaScheduler.runGlobal(task);
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    @Override
    public void runForPlayer(UUID playerUuid, Runnable task) {
        if (folia) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                foliaScheduler.runForEntity(player, task);
            }
        } else {
            runOnMainThread(task);
        }
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        if (folia) {
            long delayMs = delayTicks * 50L;
            delayExecutor.schedule(() -> foliaScheduler.runGlobal(task),
                    delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    @Override
    public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {
        if (folia) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                foliaScheduler.runForEntityLater(player, task, delayTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    @Override
    public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        if (folia) {
            Object foliaTask = foliaScheduler.runAsyncTimer(task, delay, period, unit);
            return () -> foliaScheduler.cancelFoliaTask(foliaTask);
        } else {
            long delayTicks = unit.toSeconds(delay) * 20L;
            long periodTicks = unit.toSeconds(period) * 20L;
            if (delayTicks < 1) delayTicks = 1;
            if (periodTicks < 1) periodTicks = 1;
            BukkitTask bukkitTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return bukkitTask::cancel;
        }
    }

    @Override
    public void cancelTask(BridgeTask task) {
        if (task != null) task.cancel();
    }

    public void shutdown() {
        if (delayExecutor != null) {
            delayExecutor.shutdownNow();
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
