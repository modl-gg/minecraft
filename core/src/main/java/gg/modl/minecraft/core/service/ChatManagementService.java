package gg.modl.minecraft.core.service;

import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide chat state: toggle (enable/disable) and slow mode.
 * Staff members bypass both restrictions.
 */
public class ChatManagementService {
    private static final long MILLIS_PER_SECOND = 1000L;

    @Getter private volatile boolean chatEnabled = true;
    private volatile int slowModeSeconds = 0;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    /** @return the new state (true = enabled) */
    public boolean toggleChat() {
        chatEnabled = !chatEnabled;
        return chatEnabled;
    }

    public void setSlowMode(int seconds) {
        this.slowModeSeconds = Math.max(0, seconds);
        if (seconds <= 0) lastMessageTime.clear();
    }

    public void disableSlowMode() {
        this.slowModeSeconds = 0;
        lastMessageTime.clear();
    }

    public boolean canSendMessage(UUID playerUuid, boolean isStaff) {
        if (isStaff) return true;
        if (!chatEnabled) return false;
        if (slowModeSeconds <= 0) return true;

        Long lastTime = lastMessageTime.get(playerUuid);
        if (lastTime != null) {
            long elapsed = System.currentTimeMillis() - lastTime;
            if (elapsed < slowModeSeconds * MILLIS_PER_SECOND) return false;
        }
        lastMessageTime.put(playerUuid, System.currentTimeMillis());
        return true;
    }

    /** @return seconds remaining before the player can send again, or 0 */
    public int getSlowModeRemaining(UUID playerUuid) {
        if (slowModeSeconds <= 0) return 0;
        Long lastTime = lastMessageTime.get(playerUuid);
        if (lastTime == null) return 0;

        long remaining = (slowModeSeconds * MILLIS_PER_SECOND) - (System.currentTimeMillis() - lastTime);
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / (double) MILLIS_PER_SECOND);
    }

    public void removePlayer(UUID playerUuid) {
        lastMessageTime.remove(playerUuid);
    }
}
