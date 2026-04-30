package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.util.PluginLogger;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeReplayServiceTest {

    @Test
    void captureReplayFailsImmediatelyWhenNoBackendIsConnected() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(false);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(UUID.randomUUID(), "byteful");

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(0, broadcaster.sentActions.size());
        assertTrue(logger.warnings.stream().anyMatch(message -> message.contains("No connected backends")));

        service.shutdown();
    }

    @Test
    void captureReplayDispatchesAndCompletesFromBackendResponse() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals("CAPTURE_REPLAY", broadcaster.sentActions.get(0));
        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.OK, captureResult.getStatus());
        assertEquals("replay-123", captureResult.getReplayId());

        service.shutdown();
    }

    @Test
    void captureReplayFailsImmediatelyWhenDispatchFails() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true, 0);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(UUID.randomUUID(), "byteful");

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(1, broadcaster.sentActions.size());
        assertTrue(logger.warnings.stream().anyMatch(message -> message.contains("Failed to dispatch")));

        service.shutdown();
    }

    @Test
    void captureReplayJoinsExistingPendingCaptureForSamePlayer() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> firstResult = service.captureReplayResult(targetUuid, "byteful");
        CompletableFuture<ReplayCaptureResult> secondResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(firstResult, secondResult);
        assertEquals(1, broadcaster.sentActions.size());
        assertEquals("replay-123", firstResult.get(100, TimeUnit.MILLISECONDS).getReplayId());
        assertEquals("replay-123", secondResult.get(100, TimeUnit.MILLISECONDS).getReplayId());

        service.shutdown();
    }

    @Test
    void captureReplayWaitsForAllNotLocalBackendResponses() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true, 2);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);

        assertFalse(result.isDone());

        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, result.get(100, TimeUnit.MILLISECONDS).getStatus());

        service.shutdown();
    }

    @Test
    void captureReplayPrioritizesFabricDisabledOverNotLocal() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true, 2);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);

        assertFalse(result.isDone());

        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.FABRIC_DISABLED);

        assertEquals(ReplayCaptureStatus.FABRIC_DISABLED, result.get(100, TimeUnit.MILLISECONDS).getStatus());

        service.shutdown();
    }

    @Test
    void captureReplayPrioritizesErrorOverNotLocal() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true, 2);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);

        assertFalse(result.isDone());

        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertEquals(ReplayCaptureStatus.ERROR, result.get(100, TimeUnit.MILLISECONDS).getStatus());

        service.shutdown();
    }

    @Test
    void captureReplayCompletesWhenNonOkResponseArrivesBeforeDispatchCountIsSet() throws Exception {
        UUID targetUuid = UUID.randomUUID();
        RecordingLogger logger = new RecordingLogger();
        final BridgeReplayService[] serviceRef = new BridgeReplayService[1];
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true) {
            @Override
            public int sendToAllBridges(String action, String... args) {
                int dispatched = super.sendToAllBridges(action, args);
                serviceRef[0].handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.FABRIC_DISABLED);
                return dispatched;
            }
        };
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        serviceRef[0] = service;

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");

        assertEquals(ReplayCaptureStatus.FABRIC_DISABLED, result.get(100, TimeUnit.MILLISECONDS).getStatus());

        service.shutdown();
    }

    @Test
    void captureReplayKeepsWaitingWhenNotLocalResponseArrivesBeforeReplayId() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true, 2);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.OK, captureResult.getStatus());
        assertEquals("replay-123", captureResult.getReplayId());

        service.shutdown();
    }

    @Test
    void captureReplayPreservesOldTwoFieldResponseCompatibility() throws Exception {
        RecordingBroadcaster broadcaster = new RecordingBroadcaster(true);
        RecordingLogger logger = new RecordingLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123");

        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.OK, captureResult.getStatus());
        assertEquals("replay-123", captureResult.getReplayId());

        service.shutdown();
    }

    private static class RecordingBroadcaster implements BridgeBroadcaster {
        private final boolean connected;
        private final int dispatchCount;
        private final List<String> sentActions = new ArrayList<>();

        private RecordingBroadcaster(boolean connected) {
            this(connected, connected ? 1 : 0);
        }

        private RecordingBroadcaster(boolean connected, int dispatchCount) {
            this.connected = connected;
            this.dispatchCount = dispatchCount;
        }

        @Override
        public int sendToAllBridges(String action, String... args) {
            sentActions.add(action);
            return dispatchCount;
        }

        @Override
        public boolean hasConnectedClients() {
            return connected;
        }
    }

    private static final class RecordingLogger implements PluginLogger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message) {
        }

        @Override
        public void warning(String message) {
            warnings.add(message);
        }

        @Override
        public void severe(String message) {
        }
    }
}
