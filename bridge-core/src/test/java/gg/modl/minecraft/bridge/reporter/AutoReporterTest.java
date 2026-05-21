package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.core.service.ReplayCaptureResult;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.service.ReplayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoReporterTest {

    private static final DetectionSource SOURCE = DetectionSource.GRIM;
    private static final String CHECK_NAME = "speed";
    private static final String PLAYER_NAME = "byteful";

    @TempDir
    Path tempDir;

    @Test
    void successfulReplayCaptureCreatesTicketWithReplayId() throws IOException {
        TestFixture fixture = createFixture();
        CompletableFuture<ReplayCaptureResult> replayFuture = new CompletableFuture<>();
        fixture.replayService.status = ReplayCaptureStatus.OK;
        fixture.replayService.captureFuture = replayFuture;
        fixture.autoReporter.setReplayService(fixture.replayService);
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        replayFuture.complete(ReplayCaptureResult.ok("replay-123"));

        assertEquals(1, fixture.ticketCreator.tickets.size());
        assertEquals("replay-123", fixture.ticketCreator.tickets.get(0).replayUrl);
    }

    @Test
    void failedReplayCaptureStillCreatesTicketWithoutReplayId() throws IOException {
        TestFixture fixture = createFixture();
        CompletableFuture<ReplayCaptureResult> replayFuture = new CompletableFuture<>();
        fixture.replayService.status = ReplayCaptureStatus.OK;
        fixture.replayService.captureFuture = replayFuture;
        fixture.autoReporter.setReplayService(fixture.replayService);
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        replayFuture.completeExceptionally(new IOException("upload failed"));

        assertEquals(1, fixture.ticketCreator.tickets.size());
        assertNull(fixture.ticketCreator.tickets.get(0).replayUrl);
    }

    @Test
    void coldReplayStatusStillAttemptsCaptureBeforeCreatingTicket() throws IOException {
        TestFixture fixture = createFixture();
        CompletableFuture<ReplayCaptureResult> replayFuture = new CompletableFuture<>();
        fixture.replayService.status = ReplayCaptureStatus.NO_ACTIVE_RECORDING;
        fixture.replayService.captureFuture = replayFuture;
        fixture.autoReporter.setReplayService(fixture.replayService);
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);

        assertEquals(1, fixture.replayService.captureAttempts);
        assertEquals(0, fixture.ticketCreator.tickets.size());

        replayFuture.complete(ReplayCaptureResult.ok("replay-123"));

        assertEquals(1, fixture.ticketCreator.tickets.size());
        assertEquals("replay-123", fixture.ticketCreator.tickets.get(0).replayUrl);
    }

    @Test
    void pendingReplayCaptureSuppressesDuplicateTicketCreation() throws IOException {
        TestFixture fixture = createFixture();
        CompletableFuture<ReplayCaptureResult> replayFuture = new CompletableFuture<>();
        fixture.replayService.status = ReplayCaptureStatus.OK;
        fixture.replayService.captureFuture = replayFuture;
        fixture.autoReporter.setReplayService(fixture.replayService);
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        replayFuture.complete(ReplayCaptureResult.ok("replay-123"));

        assertEquals(1, fixture.ticketCreator.tickets.size());
        assertEquals("replay-123", fixture.ticketCreator.tickets.get(0).replayUrl);
    }

    @Test
    void cooldownAppliesAfterIntendedReportSubmission() throws IOException {
        TestFixture fixture = createFixture();
        fixture.addViolation();

        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
        fixture.addViolation();
        fixture.autoReporter.checkAndReport(fixture.playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);

        assertEquals(1, fixture.ticketCreator.tickets.size());
    }

    @Test
    void ticketCreationFailureIsLoggedWithoutSettingCooldown() throws IOException {
        BridgeConfig config = loadConfig();
        ThrowingTicketCreator ticketCreator = new ThrowingTicketCreator();
        ViolationTracker violationTracker = new ViolationTracker();
        Logger logger = isolatedLogger("ticket-failure-no-replay");
        CapturingHandler handler = attachWarningHandler(logger);
        try {
            AutoReporter autoReporter = new AutoReporter(logger, config, ticketCreator, violationTracker);
            UUID playerUuid = UUID.randomUUID();
            violationTracker.addViolation(playerUuid, SOURCE, CHECK_NAME, "verbose");

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(1, ticketCreator.attempts);
            assertEquals(1, handler.warnings.size());
            assertTrue(handler.warnings.get(0).getMessage().contains("Ticket creation failed"));

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(2, ticketCreator.attempts);
            assertEquals(2, handler.warnings.size());
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void ticketCreationFailureInReplayHandlerIsLoggedWithoutSettingCooldown() throws IOException {
        BridgeConfig config = loadConfig();
        ThrowingTicketCreator ticketCreator = new ThrowingTicketCreator();
        ViolationTracker violationTracker = new ViolationTracker();
        Logger logger = isolatedLogger("ticket-failure-with-replay");
        CapturingHandler handler = attachWarningHandler(logger);
        try {
            AutoReporter autoReporter = new AutoReporter(logger, config, ticketCreator, violationTracker);
            FakeReplayService replayService = new FakeReplayService();
            replayService.status = ReplayCaptureStatus.OK;
            replayService.captureFuture = CompletableFuture.completedFuture(ReplayCaptureResult.ok("replay-xyz"));
            autoReporter.setReplayService(replayService);
            UUID playerUuid = UUID.randomUUID();
            violationTracker.addViolation(playerUuid, SOURCE, CHECK_NAME, "verbose");

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(1, ticketCreator.attempts);
            assertEquals(1, handler.warnings.size());
            assertTrue(handler.warnings.get(0).getMessage().contains("Ticket creation failed"));

            autoReporter.checkAndReport(playerUuid, PLAYER_NAME, SOURCE, CHECK_NAME);
            assertEquals(2, ticketCreator.attempts);
            assertEquals(2, handler.warnings.size());
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static Logger isolatedLogger(String name) {
        Logger logger = Logger.getLogger("AutoReporterTest." + name + "." + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static CapturingHandler attachWarningHandler(Logger logger) {
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        return handler;
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> warnings = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record);
            }
        }

        @Override public void flush() {}
        @Override public void close() throws SecurityException {}
    }

    private TestFixture createFixture() throws IOException {
        BridgeConfig config = loadConfig();
        RecordingTicketCreator ticketCreator = new RecordingTicketCreator();
        ViolationTracker violationTracker = new ViolationTracker();
        AutoReporter autoReporter = new AutoReporter(
                Logger.getLogger("test"),
                config,
                ticketCreator,
                violationTracker
        );
        return new TestFixture(autoReporter, ticketCreator, violationTracker, new FakeReplayService());
    }

    private BridgeConfig loadConfig() throws IOException {
        Files.write(tempDir.resolve("bridge-config.yml"), String.join(System.lineSeparator(),
                "anticheat-name: \"TestAC\"",
                "server-name: \"TestServer\"",
                "report-cooldown: 60",
                "report-violation-threshold:",
                "  default: 1"
        ).getBytes(StandardCharsets.UTF_8));
        return BridgeConfig.load(tempDir);
    }

    private static final class TestFixture {
        private final UUID playerUuid = UUID.randomUUID();
        private final AutoReporter autoReporter;
        private final RecordingTicketCreator ticketCreator;
        private final ViolationTracker violationTracker;
        private final FakeReplayService replayService;

        private TestFixture(AutoReporter autoReporter, RecordingTicketCreator ticketCreator,
                            ViolationTracker violationTracker, FakeReplayService replayService) {
            this.autoReporter = autoReporter;
            this.ticketCreator = ticketCreator;
            this.violationTracker = violationTracker;
            this.replayService = replayService;
        }

        private void addViolation() {
            violationTracker.addViolation(playerUuid, SOURCE, CHECK_NAME, "verbose");
        }
    }

    private static final class ThrowingTicketCreator implements TicketCreator {
        private int attempts;

        @Override
        public void createTicket(String creatorUuid, String creatorName, String type, String subject,
                                 String description, String reportedPlayerUuid, String reportedPlayerName,
                                 String tagsJoined, String priority, String createdServer, String replayUrl) {
            attempts++;
            throw new RuntimeException("ticket creation failed");
        }
    }

    private static final class RecordingTicketCreator implements TicketCreator {
        private final List<CreatedTicket> tickets = new ArrayList<>();

        @Override
        public void createTicket(String creatorUuid, String creatorName, String type, String subject,
                                 String description, String reportedPlayerUuid, String reportedPlayerName,
                                 String tagsJoined, String priority, String createdServer, String replayUrl) {
            tickets.add(new CreatedTicket(replayUrl));
        }
    }

    private static final class CreatedTicket {
        private final String replayUrl;

        private CreatedTicket(String replayUrl) {
            this.replayUrl = replayUrl;
        }
    }

    private static final class FakeReplayService implements ReplayService {
        private ReplayCaptureStatus status = ReplayCaptureStatus.NO_ACTIVE_RECORDING;
        private int captureAttempts;
        private CompletableFuture<ReplayCaptureResult> captureFuture =
                CompletableFuture.completedFuture(ReplayCaptureResult.noActiveRecording());

        @Override
        public CompletableFuture<ReplayCaptureResult> captureReplayResult(UUID targetUuid, String targetName) {
            captureAttempts++;
            return captureFuture;
        }

        @Override
        public ReplayCaptureStatus getReplayStatus(UUID playerUuid) {
            return status;
        }
    }
}
