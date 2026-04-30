package gg.modl.minecraft.fabric.v1_21_1;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FabricBridgeScheduler implements BridgeScheduler {
    private final MinecraftServer server;
    private final ScheduledExecutorService asyncExecutor;

    public FabricBridgeScheduler(MinecraftServer server) {
        this.server = server;
        this.asyncExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "modl-bridge-async");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void runSync(Runnable task) {
        server.execute(task);
    }

    @Override
    public void runForPlayer(UUID playerUuid, Runnable task) {
        // Fabric doesn't have region-based scheduling, use main thread
        server.execute(task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50L; // 1 tick = 50ms
        asyncExecutor.schedule(() -> server.execute(task), delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {
        runLater(task, delayTicks);
    }

    @Override
    public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        ScheduledFuture<?> future = asyncExecutor.scheduleAtFixedRate(task, delay, period, unit);
        return () -> future.cancel(false);
    }

    @Override
    public void cancelTask(BridgeTask task) {
        if (task != null) task.cancel();
    }

    public void shutdown() {
        asyncExecutor.shutdownNow();
    }
}
