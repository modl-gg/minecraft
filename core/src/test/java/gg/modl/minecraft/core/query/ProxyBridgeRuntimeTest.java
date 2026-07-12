package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.support.RecordingBridgeBroadcaster;
import gg.modl.minecraft.core.support.RecordingPluginLogger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyBridgeRuntimeTest {

    @Test
    void shutdownStopsBridgeServerAndReplayScheduler() {
        RecordingPluginLogger logger = new RecordingPluginLogger();
        AtomicBoolean bridgeShutdown = new AtomicBoolean(false);
        BridgeReplayService replayService = new BridgeReplayService(new RecordingBridgeBroadcaster(false), logger);
        ProxyBridgeRuntime runtime = new ProxyBridgeRuntime(() -> bridgeShutdown.set(true), replayService);

        runtime.shutdown();

        assertTrue(bridgeShutdown.get());
        assertTrue(replayService.isShutdown());
    }
}
