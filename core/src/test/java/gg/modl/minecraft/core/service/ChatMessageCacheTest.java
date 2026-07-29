package gg.modl.minecraft.core.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageCacheTest {
    private static final String SERVER = "lobby";
    private static final String PLAYER_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String PLAYER_NAME = "Notch";
    private static final int CAPACITY_BELOW_REPORT_LOOKBACK = 5;
    private static final long MAX_AGE_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long IMMEDIATE_EXPIRY_MS = 1;

    @Test
    void oldestMessagesAreEvictedOnceCapacityIsReached() {
        ChatMessageCache cache = new ChatMessageCache(CAPACITY_BELOW_REPORT_LOOKBACK, MAX_AGE_MS);
        for (int i = 0; i < CAPACITY_BELOW_REPORT_LOOKBACK + 3; i++) {
            cache.addMessage(SERVER, PLAYER_UUID, PLAYER_NAME, "m" + i);
        }

        String[] lines = cache.getChatLogForReport(PLAYER_UUID).split("\n");

        assertEquals(CAPACITY_BELOW_REPORT_LOOKBACK, lines.length);
        assertTrue(lines[0].endsWith(": m3"));
        assertTrue(lines[CAPACITY_BELOW_REPORT_LOOKBACK - 1].endsWith(": m7"));
    }

    @Test
    void messagesOlderThanTheRetentionWindowAreDropped() throws InterruptedException {
        ChatMessageCache cache = new ChatMessageCache(CAPACITY_BELOW_REPORT_LOOKBACK, IMMEDIATE_EXPIRY_MS);
        cache.addMessage(SERVER, PLAYER_UUID, PLAYER_NAME, "stale");
        Thread.sleep(10);

        assertTrue(cache.getChatLogForReport(PLAYER_UUID).isEmpty());
    }

    @Test
    void blankMessagesAreNotCached() {
        ChatMessageCache cache = new ChatMessageCache(CAPACITY_BELOW_REPORT_LOOKBACK, MAX_AGE_MS);
        cache.addMessage(SERVER, PLAYER_UUID, PLAYER_NAME, "   ");

        assertTrue(cache.getChatLogForReport(PLAYER_UUID).isEmpty());
    }

    @Test
    void messagesFromOtherServersAreExcluded() {
        ChatMessageCache cache = new ChatMessageCache(CAPACITY_BELOW_REPORT_LOOKBACK, MAX_AGE_MS);
        cache.addMessage(SERVER, PLAYER_UUID, PLAYER_NAME, "reported");
        cache.addMessage("survival", "22222222-2222-2222-2222-222222222222", "Herobrine", "unrelated");

        String log = cache.getChatLogForReport(PLAYER_UUID);

        assertTrue(log.endsWith(": reported"));
        assertFalse(log.contains("unrelated"));
    }

    @Test
    void aCapacityThatCouldNeverHoldAMessageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChatMessageCache(0, MAX_AGE_MS));
    }
}
