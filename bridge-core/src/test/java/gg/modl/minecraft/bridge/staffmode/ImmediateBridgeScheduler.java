package gg.modl.minecraft.bridge.staffmode;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

class ImmediateBridgeScheduler implements BridgeScheduler {
    private final FakeStaffModeOps ops;

    private Runnable timer;

    ImmediateBridgeScheduler(FakeStaffModeOps ops) {
        this.ops = ops;
    }

    void runTimerOnce() {
        if (timer != null) timer.run();
    }

    @Override
    public void runOnMainThread(Runnable task) {
        task.run();
    }

    @Override
    public void runForPlayer(UUID playerUuid, Runnable task) {
        if (!ops.isOnline(playerUuid)) return;
        task.run();
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        task.run();
    }

    @Override
    public void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks) {
        if (!ops.isOnline(playerUuid)) return;
        task.run();
    }

    @Override
    public BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        timer = task;
        return () -> timer = null;
    }

    @Override
    public void cancelTask(BridgeTask task) {
    }
}
