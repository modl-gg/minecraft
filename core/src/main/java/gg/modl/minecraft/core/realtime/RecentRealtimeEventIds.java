package gg.modl.minecraft.core.realtime;

import java.util.LinkedHashSet;
import java.util.Set;

class RecentRealtimeEventIds {
    private final int capacity;
    private final Set<String> eventIds = new LinkedHashSet<>();

    RecentRealtimeEventIds(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean markIfNew(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return true;
        }
        if (eventIds.contains(eventId)) {
            return false;
        }
        eventIds.add(eventId);
        while (eventIds.size() > capacity) {
            String oldest = eventIds.iterator().next();
            eventIds.remove(oldest);
        }
        return true;
    }
}
