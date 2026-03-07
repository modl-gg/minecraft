package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import lombok.Getter;

import java.util.UUID;

/**
 * Server-wide chat state: toggle (enable/disable) and slow mode.
 * Staff members bypass both restrictions.
 * Per-player slow mode tracking is stored in PlayerProfile.
 */
public class ChatManagementService {
    private static final long MILLIS_PER_SECOND = 1000L;

    private final CachedProfileRegistry registry;
    @Getter private volatile boolean chatEnabled = true;
    private volatile int slowModeSeconds = 0;

    public ChatManagementService(CachedProfileRegistry registry) {
        this.registry = registry;
    }

    /** @return the new state (true = enabled) */
    public boolean toggleChat() {
        chatEnabled = !chatEnabled;
        return chatEnabled;
    }

    public void setSlowMode(int seconds) {
        this.slowModeSeconds = Math.max(0, seconds);
    }

    public void disableSlowMode() {
        this.slowModeSeconds = 0;
    }

    public boolean canSendMessage(UUID playerUuid, boolean isStaff) {
        if (isStaff) return true;
        if (!chatEnabled) return false;
        if (slowModeSeconds <= 0) return true;

        CachedProfile profile = registry.getProfile(playerUuid);
        if (profile == null) return true;

        long lastTime = profile.getLastChatMessageTime();
        if (lastTime != 0) {
            long elapsed = System.currentTimeMillis() - lastTime;
            if (elapsed < slowModeSeconds * MILLIS_PER_SECOND) return false;
        }
        profile.setLastChatMessageTime(System.currentTimeMillis());
        return true;
    }

    /** @return seconds remaining before the player can send again, or 0 */
    public int getSlowModeRemaining(UUID playerUuid) {
        if (slowModeSeconds <= 0) return 0;
        CachedProfile profile = registry.getProfile(playerUuid);
        if (profile == null) return 0;

        long lastTime = profile.getLastChatMessageTime();
        if (lastTime == 0) return 0;

        long remaining = (slowModeSeconds * MILLIS_PER_SECOND) - (System.currentTimeMillis() - lastTime);
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / (double) MILLIS_PER_SECOND);
    }
}
