package gg.modl.minecraft.bridge;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public interface BridgeScheduler {

    void runSync(Runnable task);

    void runForPlayer(UUID playerUuid, Runnable task);

    void runLater(Runnable task, long delayTicks);

    void runForPlayerLater(UUID playerUuid, Runnable task, long delayTicks);

    BridgeTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit);

    void cancelTask(BridgeTask task);
}
