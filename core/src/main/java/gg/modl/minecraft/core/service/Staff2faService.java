package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.config.ConfigManager.Staff2faConfig;
import lombok.Setter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff two-factor authentication. Session validity comes from the backend
 * via sync responses — no local session/IP caching.
 */
public class Staff2faService {
    public enum AuthState {
        PENDING,
        AUTHENTICATED
    }

    private final Map<UUID, AuthState> authStates = new ConcurrentHashMap<>();
    /** Tracks players already notified about pending verification to avoid repeat messages. */
    private final Set<UUID> notifiedPending = ConcurrentHashMap.newKeySet();
    @Setter private volatile Staff2faConfig config;

    public Staff2faService(Staff2faConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public boolean isAuthenticated(UUID uuid) {
        if (!isEnabled()) return true;
        return authStates.get(uuid) == AuthState.AUTHENTICATED;
    }

    /**
     * If 2FA is disabled the player is immediately authenticated,
     * otherwise placed in PENDING until the backend session is validated.
     */
    public void onStaffJoin(UUID uuid) {
        authStates.put(uuid, isEnabled() ? AuthState.PENDING : AuthState.AUTHENTICATED);
    }

    public void handleVerification(UUID uuid) {
        authStates.put(uuid, AuthState.AUTHENTICATED);
        notifiedPending.remove(uuid);
    }

    /** @return true if this is the first notification (was not already notified) */
    public boolean markNotified(UUID uuid) {
        return notifiedPending.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        authStates.remove(uuid);
        notifiedPending.remove(uuid);
    }

    public AuthState getAuthState(UUID uuid) {
        return authStates.get(uuid);
    }
}
