package gg.modl.minecraft.bridge.staffmode;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

class ImmediateBridgeScheduler implements BridgeScheduler {
    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void runForPlayer(UUID playerUuid, Runnable task) {
        task.run();
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        task.run();
    }

    @Override
    public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {
        task.run();
    }

    @Override
    public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        return () -> {
        };
    }

    @Override
    public void cancelTask(BridgeTask task) {
    }
}
