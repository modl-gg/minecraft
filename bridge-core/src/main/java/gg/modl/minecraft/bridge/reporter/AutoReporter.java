package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationRecord;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.core.service.ReplayCaptureStatus;
import gg.modl.minecraft.core.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AutoReporter {
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final String TICKET_TYPE = "player";
    private static final String TICKET_PRIORITY = "NORMAL";

    private final Logger logger;
    private final BridgeConfig config;
    private final TicketCreator ticketCreator;
    private final ViolationTracker violationTracker;

    @Setter private ReplayService replayService;

    private final ConcurrentHashMap<UUID, Long> reportCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> reportsInFlight = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean loggedMissingCreator = new java.util.concurrent.atomic.AtomicBoolean(false);

    public void checkAndReport(UUID uuid, String playerName, DetectionSource source, String checkName) {
        if (ticketCreator == null) {
            if (loggedMissingCreator.compareAndSet(false, true)) {
                logger.warning("[bridge] Auto-report skipped: no TicketCreator configured for this platform/mode");
            }
            return;
        }
        int vl = violationTracker.getViolationCount(uuid, source, checkName);
        int threshold = config.getReportViolationThreshold(checkName);

        if (config.isDebug()) {
            logger.info("[DEBUG] checkAndReport: player=" + playerName
                    + " source=" + source.name() + " check=" + checkName
                    + " vl=" + vl + " threshold=" + threshold);
        }

        if (vl < threshold) return;
        if (isOnCooldown(uuid, playerName)) return;
        if (!tryStartReport(uuid, playerName)) return;

        submitReport(uuid, playerName, source, checkName, vl);
    }

    private boolean isOnCooldown(UUID uuid, String playerName) {
        Long lastReport = reportCooldowns.get(uuid);
        if (lastReport == null) return false;

        long now = System.currentTimeMillis();
        long cooldownMs = config.getReportCooldown() * MILLIS_PER_SECOND;
        long elapsed = now - lastReport;

        if (elapsed >= cooldownMs) return false;

        if (config.isDebug()) {
            long remainingSeconds = (cooldownMs - elapsed) / MILLIS_PER_SECOND;
            logger.info("[DEBUG] Report blocked by cooldown for " + playerName
                    + " (" + remainingSeconds + "s remaining)");
        }
        return true;
    }

    private boolean tryStartReport(UUID uuid, String playerName) {
        boolean alreadyInFlight = reportsInFlight.putIfAbsent(uuid, Boolean.TRUE) != null;
        if (alreadyInFlight && config.isDebug()) {
            logger.info("[DEBUG] Report blocked by pending submission for " + playerName);
        }
        return !alreadyInFlight;
    }

    private void submitReport(UUID uuid, String playerName, DetectionSource source, String checkName, int vl) {
        List<ViolationRecord> records = violationTracker.getRecords(uuid);
        String anticheatName = config.getAnticheatName();
        String uuidStr = uuid.toString();

        String subject = "[" + anticheatName + "] " + checkName + " - " + playerName + " (VL: " + vl + ")";

        String description = "**Automated anticheat report.**\n\n" +
                "**Player:** " + playerName + "\n\n" +
                "**Trigger:** " + source.name() + " " + checkName + " (VL: " + vl + ")\n\n" +
                "**Recent violations:**\n```\n" +
                records.stream()
                        .map(ViolationRecord::toString)
                        .collect(Collectors.joining("\n")) +
                "\n```";

        logger.info("Auto-report triggered for " + playerName
                + " (" + source.name() + " " + checkName + " VL: " + vl + ")");

        if (replayService != null && shouldAttemptReplayCapture(uuid)) {
            CompletableFuture<String> replayFuture;
            try {
                replayFuture = replayService.captureReplay(uuid, playerName);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "[bridge] Replay capture failed for auto-report: " + playerName, e);
                createTicketWithoutReplay(uuid, uuidStr, anticheatName, subject, description, playerName);
                return;
            }
            replayFuture.handle((replayId, throwable) -> {
                try {
                    String reportReplayId = replayId;
                    if (throwable != null) {
                        logger.log(Level.WARNING, "[bridge] Replay capture failed for auto-report: " + playerName, throwable);
                        reportReplayId = null;
                    }
                    if (replayId != null) {
                        logger.info("[bridge] Replay captured for auto-report: " + playerName + " -> " + replayId);
                    }
                    try {
                        ticketCreator.createTicket(
                                uuidStr, anticheatName, TICKET_TYPE, subject, description,
                                uuidStr, playerName, null, TICKET_PRIORITY, config.getServerName(), reportReplayId
                        );
                        markReportSubmitted(uuid);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[bridge] Ticket creation failed for auto-report: " + playerName, e);
                    }
                } finally {
                    reportsInFlight.remove(uuid);
                }
                return null;
            });
        } else {
            createTicketWithoutReplay(uuid, uuidStr, anticheatName, subject, description, playerName);
        }
    }

    private boolean shouldAttemptReplayCapture(UUID uuid) {
        ReplayCaptureStatus status = replayService.getReplayStatus(uuid);
        return status != ReplayCaptureStatus.NO_BRIDGE_CONNECTED
                && status != ReplayCaptureStatus.FABRIC_DISABLED;
    }

    private void createTicketWithoutReplay(UUID uuid, String uuidStr, String anticheatName,
                                           String subject, String description, String playerName) {
        try {
            try {
                ticketCreator.createTicket(
                        uuidStr, anticheatName, TICKET_TYPE, subject, description,
                        uuidStr, playerName, null, TICKET_PRIORITY, config.getServerName()
                );
                markReportSubmitted(uuid);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[bridge] Ticket creation failed for auto-report: " + playerName, e);
            }
        } finally {
            reportsInFlight.remove(uuid);
        }
    }

    private void markReportSubmitted(UUID uuid) {
        reportCooldowns.put(uuid, System.currentTimeMillis());
        violationTracker.resetPlayer(uuid);
    }

    public void clearCooldown(UUID uuid) {
        reportCooldowns.remove(uuid);
    }
}
