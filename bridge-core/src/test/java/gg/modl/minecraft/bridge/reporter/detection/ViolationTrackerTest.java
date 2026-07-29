package gg.modl.minecraft.bridge.reporter.detection;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ViolationTrackerTest {

    @Test
    void cleanupRemovesPlayerEntryWhenAllRecordsExpire() throws Exception {
        ViolationTracker tracker = new ViolationTracker();
        UUID playerUuid = UUID.randomUUID();
        ConcurrentHashMap<UUID, Deque<ViolationRecord>> records = getRecords(tracker);
        Deque<ViolationRecord> expiredRecords = new ArrayDeque<>();
        expiredRecords.add(expiredViolation());
        records.put(playerUuid, expiredRecords);

        invokeCleanup(tracker);

        assertFalse(records.containsKey(playerUuid));
    }

    @Test
    void cleanupDoesNotDropViolationAddedToPlayerWithExpiredRecords() throws Exception {
        ViolationTracker tracker = new ViolationTracker();
        CountDownLatch addPaused = new CountDownLatch(1);
        CountDownLatch continueAdd = new CountDownLatch(1);
        tracker.setBeforeAddHook((uuid, playerRecords) -> {
            addPaused.countDown();
            await(continueAdd);
        });

        UUID playerUuid = UUID.randomUUID();
        ConcurrentHashMap<UUID, Deque<ViolationRecord>> records = getRecords(tracker);
        Deque<ViolationRecord> expiredRecords = new ArrayDeque<>();
        expiredRecords.add(expiredViolation());
        records.put(playerUuid, expiredRecords);

        Thread addViolation = new Thread(
                () -> tracker.addViolation(playerUuid, DetectionSource.GRIM, "Speed", "fresh"),
                "add-violation");

        addViolation.start();
        await(addPaused);

        Thread cleanup = new Thread(() -> invokeCleanupUnchecked(tracker), "cleanup-violations");
        cleanup.start();
        waitUntilCleanupCompletedOrBlocked(cleanup);

        continueAdd.countDown();

        addViolation.join(TimeUnit.SECONDS.toMillis(1));
        cleanup.join(TimeUnit.SECONDS.toMillis(1));
        assertFalse(addViolation.isAlive());
        assertFalse(cleanup.isAlive());

        List<ViolationRecord> remainingRecords = tracker.getRecords(playerUuid);
        assertEquals(1, remainingRecords.size());
        assertEquals("fresh", remainingRecords.get(0).getVerbose());
    }

    private static ViolationRecord expiredViolation() {
        return new ViolationRecord(DetectionSource.GRIM, "Speed", "expired", 0L);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<UUID, Deque<ViolationRecord>> getRecords(ViolationTracker tracker) throws Exception {
        Field recordsField = ViolationTracker.class.getDeclaredField("records");
        recordsField.setAccessible(true);
        return (ConcurrentHashMap<UUID, Deque<ViolationRecord>>) recordsField.get(tracker);
    }

    private void invokeCleanup(ViolationTracker tracker) throws Exception {
        Method cleanupMethod = ViolationTracker.class.getDeclaredMethod("cleanup");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(tracker);
    }

    private void invokeCleanupUnchecked(ViolationTracker tracker) {
        try {
            invokeCleanup(tracker);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void waitUntilCleanupCompletedOrBlocked(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.isAlive() && thread.getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for cleanup to complete or block");
            }
            Thread.yield();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrency checkpoint");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrency checkpoint", e);
        }
    }
}
