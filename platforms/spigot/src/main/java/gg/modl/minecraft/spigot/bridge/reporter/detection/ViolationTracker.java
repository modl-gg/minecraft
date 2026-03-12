package gg.modl.minecraft.spigot.bridge.reporter.detection;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationTracker {
    private static final int MAX_RECORDS_PER_PLAYER = 200;
    private static final long RECORD_TTL_MS = 10 * 60 * 1000L; // 10 minutes
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // 60 seconds

    private final ConcurrentHashMap<UUID, Deque<ViolationRecord>> records = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public void startCleanupTask(JavaPlugin plugin) {
        cleanupTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanup, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
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
        if (playerRecords == null) return List.of();
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
