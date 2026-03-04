package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages network chat interception for staff members.
 * When a staff member is intercepting, they receive all chat messages
 * from across the network in real time.
 */
public class NetworkChatInterceptService {

    private final Set<UUID> intercepting = ConcurrentHashMap.newKeySet();

    /**
     * Toggle network chat interception for a staff member.
     *
     * @param uuid The UUID of the staff member
     * @return true if interception is now enabled, false if disabled
     */
    public boolean toggle(UUID uuid) {
        if (intercepting.contains(uuid)) {
            intercepting.remove(uuid);
            return false;
        } else {
            intercepting.add(uuid);
            return true;
        }
    }

    /**
     * Check if a staff member is currently intercepting network chat.
     *
     * @param uuid The UUID of the staff member
     * @return true if the staff member is intercepting
     */
    public boolean isIntercepting(UUID uuid) {
        return intercepting.contains(uuid);
    }

    /**
     * Get all staff members currently intercepting network chat.
     *
     * @return An unmodifiable set of UUIDs of intercepting staff members
     */
    public Set<UUID> getInterceptors() {
        return Collections.unmodifiableSet(intercepting);
    }

    /**
     * Remove a player from the intercepting set.
     * Should be called when a staff member disconnects.
     *
     * @param uuid The UUID of the player to remove
     */
    public void removePlayer(UUID uuid) {
        intercepting.remove(uuid);
    }
}
