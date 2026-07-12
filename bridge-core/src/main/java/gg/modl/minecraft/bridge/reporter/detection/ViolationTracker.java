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
import java.util.function.BiConsumer;

public class ViolationTracker {
    private static final int MAX_RECORDS_PER_PLAYER = 200;
    private static final long RECORD_TTL_MINUTES = 10L;
    private static final long RECORD_TTL_MS = RECORD_TTL_MINUTES * 60 * 1000L;
    private static final long CLEANUP_INTERVAL_SECONDS = 60L;

    private final ConcurrentHashMap<UUID, Deque<ViolationRecord>> records = new ConcurrentHashMap<>();
    private volatile BiConsumer<UUID, Deque<ViolationRecord>> beforeAddHook = (uuid, playerRecords) -> {};
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
        records.compute(uuid, (ignored, playerRecords) -> {
            Deque<ViolationRecord> updatedRecords = playerRecords;
            if (updatedRecords == null) {
                updatedRecords = new ArrayDeque<>();
            }
            beforeAddHook.accept(uuid, updatedRecords);
            synchronized (updatedRecords) {
                updatedRecords.addLast(new ViolationRecord(source, checkName, verbose));
                if (updatedRecords.size() > MAX_RECORDS_PER_PLAYER) {
                    updatedRecords.removeFirst();
                }
            }
            return updatedRecords;
        });
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
        for (UUID uuid : records.keySet()) {
            records.computeIfPresent(uuid, (ignored, list) -> {
                boolean empty;
                synchronized (list) {
                    list.removeIf(r -> r.getTimestamp() < cutoff);
                    empty = list.isEmpty();
                }
                return empty ? null : list;
            });
        }
    }

    void setBeforeAddHook(BiConsumer<UUID, Deque<ViolationRecord>> hook) {
        this.beforeAddHook = hook != null ? hook : (uuid, playerRecords) -> {};
    }
}
