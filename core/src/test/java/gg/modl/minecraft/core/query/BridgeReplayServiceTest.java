package gg.modl.minecraft.core.query;

import gg.modl.minecraft.core.support.RecordingBridgeBroadcaster;
import gg.modl.minecraft.core.support.RecordingPluginLogger;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeReplayServiceTest {

    @Test
    void captureReplayFailsImmediatelyWhenNoBackendIsConnected() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(false);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(UUID.randomUUID(), "byteful");

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(0, broadcaster.sentActions().size());
        assertTrue(logger.warnings().stream().anyMatch(message -> message.contains("No connected backends")));

        service.shutdown();
    }

    @Test
    void captureReplayDispatchesAndCompletesFromBackendResponse() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals("CAPTURE_REPLAY", broadcaster.sentActions().get(0));
        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.OK, captureResult.getStatus());
        assertEquals("replay-123", captureResult.getReplayId());

        service.shutdown();
    }

    @Test
    void captureReplayFailsImmediatelyWhenDispatchFails() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 0);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(UUID.randomUUID(), "byteful");

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(1, broadcaster.sentActions().size());
        assertTrue(logger.warnings().stream().anyMatch(message -> message.contains("Failed to dispatch")));

        service.shutdown();
    }

    @Test
    void captureReplayJoinsExistingPendingCaptureForSamePlayer() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> firstResult = service.captureReplayResult(targetUuid, "byteful");
        CompletableFuture<ReplayCaptureResult> secondResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(firstResult, secondResult);
        assertEquals(1, broadcaster.sentActions().size());
        assertEquals("replay-123", firstResult.get(100, TimeUnit.MILLISECONDS).getReplayId());
        assertEquals("replay-123", secondResult.get(100, TimeUnit.MILLISECONDS).getReplayId());

        service.shutdown();
    }

    @Test
    void captureReplayWaitsForAllNotLocalBackendResponses() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
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
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
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
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
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
    void captureReplayPrioritizesErrorOverFabricDisabled() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.FABRIC_DISABLED);

        assertFalse(result.isDone());

        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertEquals(ReplayCaptureStatus.ERROR, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.ERROR, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void captureReplayKeepsErrorStatusWhenFabricDisabledArrivesAfterError() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertFalse(result.isDone());

        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.FABRIC_DISABLED);

        assertEquals(ReplayCaptureStatus.ERROR, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.ERROR, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void captureReplayCompletesWhenNonOkResponseArrivesBeforeDispatchCountIsSet() throws Exception {
        UUID targetUuid = UUID.randomUUID();
        RecordingPluginLogger logger = new RecordingPluginLogger();
        final BridgeReplayService[] serviceRef = new BridgeReplayService[1];
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true) {
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
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        RecordingPluginLogger logger = new RecordingPluginLogger();
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
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        RecordingPluginLogger logger = new RecordingPluginLogger();
        BridgeReplayService service = new BridgeReplayService(broadcaster, logger);
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123");

        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.OK, captureResult.getStatus());
        assertEquals("replay-123", captureResult.getReplayId());

        service.shutdown();
    }

    @Test
    void shutdownCompletesPendingCaptureWithError() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true, 2);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");

        service.shutdown();

        ReplayCaptureResult captureResult = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(ReplayCaptureStatus.ERROR, captureResult.getStatus());
        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));

        service.handleCaptureResponse(targetUuid, "replay-after-shutdown", ReplayCaptureStatus.OK);
        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));
    }

    @Test
    void captureReplayAfterShutdownCompletesWithoutDispatching() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        service.shutdown();

        CompletableFuture<ReplayCaptureResult> result = assertDoesNotThrow(() ->
                service.captureReplayResult(UUID.randomUUID(), "byteful"));

        assertTrue(result.isDone());
        assertEquals(ReplayCaptureStatus.ERROR, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(0, broadcaster.sentActions().size());
    }

    @Test
    void shutdownDuringPreDispatchWindowCompletesWithoutDispatching() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        CountDownLatch beforeDispatch = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger(), () -> {
            beforeDispatch.countDown();
            try {
                assertTrue(releaseDispatch.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();

        CompletableFuture<ReplayCaptureResult> result = CompletableFuture.supplyAsync(() ->
                service.captureReplayResult(UUID.randomUUID(), "byteful").join(), executor);

        assertTrue(beforeDispatch.await(1, TimeUnit.SECONDS));
        service.shutdown();
        releaseDispatch.countDown();

        assertEquals(ReplayCaptureStatus.ERROR, result.get(1, TimeUnit.SECONDS).getStatus());
        assertEquals(0, broadcaster.sentActions().size());
        executor.shutdownNow();
    }

    @Test
    void getReplayStatusReturnsNoBridgeWhenNoBackendIsConnected() {
        BridgeReplayService service = new BridgeReplayService(new RecordingBridgeBroadcaster(false), new RecordingPluginLogger());

        assertEquals(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, service.getReplayStatus(UUID.randomUUID()));

        service.shutdown();
    }

    @Test
    void getReplayStatusDoesNotAssumeUnknownTargetIsRecording() {
        BridgeReplayService service = new BridgeReplayService(new RecordingBridgeBroadcaster(true), new RecordingPluginLogger());

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(UUID.randomUUID()));

        service.shutdown();
    }

    @Test
    void getReplayStatusReturnsNotLocalAfterBridgeReportsTargetNotLocal() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NOT_LOCAL);

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.NOT_LOCAL, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void getReplayStatusReturnsFabricDisabledAfterBridgeReportsFabricDisabled() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.FABRIC_DISABLED);

        assertEquals(ReplayCaptureStatus.FABRIC_DISABLED, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.FABRIC_DISABLED, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void getReplayStatusReturnsOkAfterBridgeReportsReplayId() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void getReplayStatusDowngradesFromOkToNoActiveRecordingAfterLaterResponse() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> firstResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, firstResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        CompletableFuture<ReplayCaptureResult> secondResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.NO_ACTIVE_RECORDING);

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, secondResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void getReplayStatusDowngradesFromOkToErrorAfterLaterResponse() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> firstResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, firstResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        CompletableFuture<ReplayCaptureResult> secondResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertEquals(ReplayCaptureStatus.ERROR, secondResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.ERROR, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void getReplayStatusUpgradesFromErrorToOkAfterLaterResponse() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> firstResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertEquals(ReplayCaptureStatus.ERROR, firstResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.ERROR, service.getReplayStatus(targetUuid));

        CompletableFuture<ReplayCaptureResult> secondResult = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, secondResult.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void removeTargetStatusClearsCachedStatusForTarget() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        service.removeTargetStatus(targetUuid);

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void onPlayerDisconnectClearsCachedStatusForTarget() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, result.get(100, TimeUnit.MILLISECONDS).getStatus());

        service.onPlayerDisconnect(targetUuid);

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));

        service.shutdown();
    }

    @Test
    void shutdownClearsCachedTargetStatuses() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "replay-123", ReplayCaptureStatus.OK);

        assertEquals(ReplayCaptureStatus.OK, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.OK, service.getReplayStatus(targetUuid));

        service.shutdown();

        assertEquals(ReplayCaptureStatus.NO_ACTIVE_RECORDING, service.getReplayStatus(targetUuid));
    }

    @Test
    void getReplayStatusReturnsErrorAfterBridgeReportsError() throws Exception {
        RecordingBridgeBroadcaster broadcaster = new RecordingBridgeBroadcaster(true);
        BridgeReplayService service = new BridgeReplayService(broadcaster, new RecordingPluginLogger());
        UUID targetUuid = UUID.randomUUID();

        CompletableFuture<ReplayCaptureResult> result = service.captureReplayResult(targetUuid, "byteful");
        service.handleCaptureResponse(targetUuid, "", ReplayCaptureStatus.ERROR);

        assertEquals(ReplayCaptureStatus.ERROR, result.get(100, TimeUnit.MILLISECONDS).getStatus());
        assertEquals(ReplayCaptureStatus.ERROR, service.getReplayStatus(targetUuid));

        service.shutdown();
    }
}
