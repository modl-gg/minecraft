package gg.modl.minecraft.spigot.bridge;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpigotBridgeSchedulerTest {

    @Test
    void shutdownStopsFoliaDelayExecutor() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        SpigotBridgeScheduler scheduler = new SpigotBridgeScheduler(mock(JavaPlugin.class), true, executor);

        scheduler.shutdown();

        assertTrue(executor.shutdownCalled);
    }

    private static class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private boolean shutdownCalled;

        @Override
        public void shutdown() {
            shutdownCalled = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownCalled = true;
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdownCalled;
        }

        @Override
        public boolean isTerminated() {
            return shutdownCalled;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdownCalled;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return null;
        }

        @Override
        public <V> java.util.concurrent.ScheduledFuture<V> schedule(
                java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            return null;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return null;
        }
    }
}
