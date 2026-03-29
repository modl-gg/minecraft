package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import gg.modl.minecraft.spigot.bridge.folia.FoliaSchedulerHelper;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpigotBridgeScheduler implements BridgeScheduler {
    private final JavaPlugin plugin;
    @Getter private final boolean folia;
    private final ScheduledExecutorService delayExecutor;

    public SpigotBridgeScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folia = FoliaSchedulerHelper.isFolia();
        this.delayExecutor = folia ? Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "modl-bridge-delay");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    @Override
    public void runSync(Runnable task) {
        if (folia) {
            FoliaSchedulerHelper.runGlobal(plugin, task);
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
                FoliaSchedulerHelper.runForEntity(plugin, player, task);
            }
        } else {
            runSync(task);
        }
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        if (folia) {
            long delayMs = delayTicks * 50L;
            delayExecutor.schedule(() -> FoliaSchedulerHelper.runGlobal(plugin, task),
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
                FoliaSchedulerHelper.runForEntityLater(plugin, player, task, delayTicks);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    @Override
    public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        if (folia) {
            Object foliaTask = FoliaSchedulerHelper.runAsyncTimer(plugin, task, delay, period, unit);
            return () -> FoliaSchedulerHelper.cancelFoliaTask(foliaTask);
        } else {
            long delayTicks = unit.toSeconds(delay) * 20L;
            long periodTicks = unit.toSeconds(period) * 20L;
            if (delayTicks < 1) delayTicks = 1;
            if (periodTicks < 1) periodTicks = 1;
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
            return bukkitTask::cancel;
        }
    }

    @Override
    public void cancelTask(BridgeTask task) {
        if (task != null) task.cancel();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}
