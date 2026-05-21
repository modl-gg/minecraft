package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.util.PluginLogger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyBridgeRuntimeTest {

    @Test
    void shutdownStopsBridgeServerAndReplayScheduler() {
        RecordingLogger logger = new RecordingLogger();
        AtomicBoolean bridgeShutdown = new AtomicBoolean(false);
        BridgeReplayService replayService = new BridgeReplayService(new RecordingBroadcaster(), logger);
        ProxyBridgeRuntime runtime = new ProxyBridgeRuntime(() -> bridgeShutdown.set(true), replayService);

        runtime.shutdown();

        assertTrue(bridgeShutdown.get());
        assertTrue(replayService.isShutdown());
    }

    private static final class RecordingBroadcaster implements BridgeBroadcaster {
        @Override
        public int sendToAllBridges(String action, String... args) {
            return 0;
        }

        @Override
        public boolean hasConnectedClients() {
            return false;
        }
    }

    private static final class RecordingLogger implements PluginLogger {
        @Override
        public void info(String message) {
        }

        @Override
        public void warning(String message) {
        }

        @Override
        public void severe(String message) {
        }
    }
}
