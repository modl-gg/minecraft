package gg.modl.minecraft.bridge.reporter.detection;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.BridgeTask;

import static gg.modl.minecraft.core.util.Java8Collections.listOf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ViolationTracker {
    private static final int MAX_RECORDS_PER_PLAYER = 200;
    private static final long RECORD_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private static final long CLEANUP_INTERVAL_SECONDS = 60L;

    private final ConcurrentHashMap<UUID, Deque<ViolationRecord>> records = new ConcurrentHashMap<>();
    private BridgeTask cleanupTask;

    public void startCleanupTask(BridgeScheduler scheduler) {
        cleanupTask = scheduler.runTimerAsync(
                this::cleanup, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void addViolation(UUID uuid, DetectionSource source, String checkName, String verbose) {
        Deque<ViolationRecord> playerRecords = records.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (playerRecords) {
            playerRecords.addLast(new ViolationRecord(source, checkName, verbose));
            if (playerRecords.size() > MAX_RECORDS_PER_PLAYER) {
                playerRecords.removeFirst();
            }
        }
    }

    public List<ViolationRecord> getRecords(UUID uuid) {
        Deque<ViolationRecord> playerRecords = records.get(uuid);
        if (playerRecords == null) return listOf();
        synchronized (playerRecords) {
            return new ArrayList<>(playerRecords);
        }
    }

    public int getViolationCount(UUID uuid, DetectionSource source, String checkName) {
        Deque<ViolationRecord> playerRecords = records.get(uuid);
        if (playerRecords == null) return 0;
        synchronized (playerRecords) {
            int count = 0;
            for (ViolationRecord r : playerRecords) {
                if (r.getSource() == source && r.getCheckName().equalsIgnoreCase(checkName)) {
                    count++;
                }
            }
            return count;
        }
    }

    public void resetPlayer(UUID uuid) {
        records.remove(uuid);
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - RECORD_TTL_MS;
        records.forEach((uuid, list) -> {
            synchronized (list) {
                list.removeIf(r -> r.getTimestamp() < cutoff);
            }
        });
    }
}
