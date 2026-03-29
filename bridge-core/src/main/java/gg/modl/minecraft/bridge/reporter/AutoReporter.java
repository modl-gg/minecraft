package gg.modl.minecraft.bridge.reporter;

import gg.modl.minecraft.bridge.config.BridgeConfig;
import gg.modl.minecraft.bridge.reporter.detection.DetectionSource;
import gg.modl.minecraft.bridge.reporter.detection.ViolationRecord;
import gg.modl.minecraft.bridge.reporter.detection.ViolationTracker;
import gg.modl.minecraft.core.service.ReplayService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    public void checkAndReport(UUID uuid, String playerName, DetectionSource source, String checkName) {
        int vl = violationTracker.getViolationCount(uuid, source, checkName);
        int threshold = config.getReportViolationThreshold(checkName);

        if (config.isDebug()) {
            logger.info("[DEBUG] checkAndReport: player=" + playerName
                    + " source=" + source.name() + " check=" + checkName
                    + " vl=" + vl + " threshold=" + threshold);
        }

        if (vl < threshold) return;
        if (isOnCooldown(uuid, playerName)) return;

        reportCooldowns.put(uuid, System.currentTimeMillis());
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

        if (replayService != null && replayService.isReplayAvailable(uuid)) {
            replayService.captureReplay(uuid, playerName).thenAccept(replayId -> {
                if (replayId != null) {
                    logger.info("[bridge] Replay captured for auto-report: " + playerName + " -> " + replayId);
                }
                ticketCreator.createTicket(
                        uuidStr, anticheatName, TICKET_TYPE, subject, description,
                        uuidStr, playerName, null, TICKET_PRIORITY, config.getServerName(), replayId
                );
            });
        } else {
            ticketCreator.createTicket(
                    uuidStr, anticheatName, TICKET_TYPE, subject, description,
                    uuidStr, playerName, null, TICKET_PRIORITY, config.getServerName()
            );
        }

        violationTracker.resetPlayer(uuid);
    }

    public void clearCooldown(UUID uuid) {
        reportCooldowns.remove(uuid);
    }
}
