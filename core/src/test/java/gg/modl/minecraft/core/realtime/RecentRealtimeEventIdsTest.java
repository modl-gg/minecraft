package gg.modl.minecraft.core.realtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecentRealtimeEventIdsTest {
    @Test
    void acceptsFirstEventIdAndRejectsDuplicate() {
        RecentRealtimeEventIds eventIds = new RecentRealtimeEventIds(2);

        assertTrue(eventIds.markIfNew("event-1"));
        assertFalse(eventIds.markIfNew("event-1"));
    }

    @Test
    void evictsOldestEventIdWhenCapacityIsExceeded() {
        RecentRealtimeEventIds eventIds = new RecentRealtimeEventIds(2);

        assertTrue(eventIds.markIfNew("event-1"));
        assertTrue(eventIds.markIfNew("event-2"));
        assertTrue(eventIds.markIfNew("event-3"));
        assertTrue(eventIds.markIfNew("event-1"));
    }

    @Test
    void doesNotSuppressBlankEventIds() {
        RecentRealtimeEventIds eventIds = new RecentRealtimeEventIds(2);

        assertTrue(eventIds.markIfNew(""));
        assertTrue(eventIds.markIfNew(""));
        assertTrue(eventIds.markIfNew("   "));
        assertTrue(eventIds.markIfNew("   "));
    }
}
