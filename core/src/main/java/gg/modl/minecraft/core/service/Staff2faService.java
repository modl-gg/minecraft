package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.config.ConfigManager.Staff2faConfig;
import gg.modl.minecraft.core.impl.cache.PlayerProfile;
import gg.modl.minecraft.core.impl.cache.PlayerProfileRegistry;
import lombok.Setter;

import java.util.UUID;

/**
 * Staff two-factor authentication. Session validity comes from the backend
 * via sync responses — no local session/IP caching.
 */
public class Staff2faService {
    public enum AuthState {
        PENDING,
        AUTHENTICATED
    }

    private final PlayerProfileRegistry registry;
    @Setter private volatile Staff2faConfig config;

    public Staff2faService(PlayerProfileRegistry registry, Staff2faConfig config) {
        this.registry = registry;
        this.config = config;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public boolean isAuthenticated(UUID uuid) {
        if (!isEnabled()) return true;
        PlayerProfile profile = registry.getProfile(uuid);
        return profile != null && profile.getAuthState() == AuthState.AUTHENTICATED;
    }

    /**
     * If 2FA is disabled the player is immediately authenticated,
     * otherwise placed in PENDING until the backend session is validated.
     */
    public void onStaffJoin(UUID uuid) {
        PlayerProfile profile = registry.getProfile(uuid);
        if (profile != null) profile.setAuthState(isEnabled() ? AuthState.PENDING : AuthState.AUTHENTICATED);
    }

    public void handleVerification(UUID uuid) {
        PlayerProfile profile = registry.getProfile(uuid);
        if (profile != null) {
            profile.setAuthState(AuthState.AUTHENTICATED);
            profile.setTwoFaNotified(false);
        }
    }

    /** @return true if this is the first notification (was not already notified) */
    public boolean markNotified(UUID uuid) {
        PlayerProfile profile = registry.getProfile(uuid);
        if (profile == null) return false;
        if (profile.isTwoFaNotified()) return false;
        profile.setTwoFaNotified(true);
        return true;
    }

    public AuthState getAuthState(UUID uuid) {
        PlayerProfile profile = registry.getProfile(uuid);
        return profile != null ? profile.getAuthState() : null;
    }
}
