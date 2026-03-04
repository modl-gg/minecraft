package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages server-wide chat state including chat toggle (enable/disable)
 * and slow mode. Staff members bypass both restrictions.
 */
public class ChatManagementService {

    private volatile boolean chatEnabled = true;
    private volatile int slowModeSeconds = 0;

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    // ==================== CHAT TOGGLE ====================

    /**
     * Check if chat is currently enabled.
     *
     * @return true if chat is enabled
     */
    public boolean isChatEnabled() {
        return chatEnabled;
    }

    /**
     * Toggle chat on or off.
     *
     * @return The new state (true = enabled, false = disabled)
     */
    public boolean toggleChat() {
        chatEnabled = !chatEnabled;
        return chatEnabled;
    }

    /**
     * Set the chat enabled state directly.
     *
     * @param enabled true to enable chat, false to disable
     */
    public void setChatEnabled(boolean enabled) {
        this.chatEnabled = enabled;
    }

    // ==================== SLOW MODE ====================

    /**
     * Check if slow mode is currently active.
     *
     * @return true if slow mode is active (seconds > 0)
     */
    public boolean isSlowModeActive() {
        return slowModeSeconds > 0;
    }

    /**
     * Get the current slow mode interval in seconds.
     *
     * @return The slow mode interval, or 0 if disabled
     */
    public int getSlowModeSeconds() {
        return slowModeSeconds;
    }

    /**
     * Set the slow mode interval.
     *
     * @param seconds The interval in seconds (0 to disable)
     */
    public void setSlowMode(int seconds) {
        this.slowModeSeconds = Math.max(0, seconds);
        if (seconds <= 0) {
            lastMessageTime.clear();
        }
    }

    /**
     * Disable slow mode.
     */
    public void disableSlowMode() {
        this.slowModeSeconds = 0;
        lastMessageTime.clear();
    }

    // ==================== MESSAGE CHECKS ====================

    /**
     * Check if a player can send a message, considering chat toggle and slow mode.
     * Staff members bypass both restrictions.
     *
     * @param playerUuid The player's UUID
     * @param isStaff    Whether the player is a staff member
     * @return true if the player is allowed to send a message
     */
    public boolean canSendMessage(UUID playerUuid, boolean isStaff) {
        // Staff always bypass restrictions
        if (isStaff) {
            return true;
        }

        // Check chat toggle
        if (!chatEnabled) {
            return false;
        }

        // Check slow mode
        if (slowModeSeconds > 0) {
            Long lastTime = lastMessageTime.get(playerUuid);
            if (lastTime != null) {
                long elapsed = System.currentTimeMillis() - lastTime;
                if (elapsed < (long) slowModeSeconds * 1000) {
                    return false;
                }
            }
            // Record this message time
            lastMessageTime.put(playerUuid, System.currentTimeMillis());
        }

        return true;
    }

    /**
     * Get the number of seconds remaining before a player can send another message
     * under slow mode.
     *
     * @param playerUuid The player's UUID
     * @return Seconds remaining, or 0 if the player can send now
     */
    public int getSlowModeRemaining(UUID playerUuid) {
        if (slowModeSeconds <= 0) {
            return 0;
        }

        Long lastTime = lastMessageTime.get(playerUuid);
        if (lastTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        long remaining = ((long) slowModeSeconds * 1000) - elapsed;

        if (remaining <= 0) {
            return 0;
        }

        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Remove a player's last message time entry (e.g., on disconnect).
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(UUID playerUuid) {
        lastMessageTime.remove(playerUuid);
    }
}
