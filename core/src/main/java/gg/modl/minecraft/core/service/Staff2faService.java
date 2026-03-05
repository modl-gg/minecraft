package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.config.Staff2faConfig;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing staff two-factor authentication.
 * <p>
 * When enabled, staff members must verify their identity after joining.
 * Session validity is determined by the backend (7-day TTL) and delivered
 * via sync responses — no caching of sessions or IPs on the plugin side.
 */
public class Staff2faService {

    /**
     * Authentication state for a staff member.
     */
    public enum AuthState {
        /** Staff has joined but has not yet verified. */
        PENDING,
        /** Staff has completed verification. */
        AUTHENTICATED
    }

    /** Current authentication state per player. */
    private final Map<UUID, AuthState> authStates = new ConcurrentHashMap<>();

    /** Players who have been notified that they need to verify (to avoid repeating the message). */
    private final Set<UUID> notifiedPending = ConcurrentHashMap.newKeySet();

    private volatile Staff2faConfig config;

    public Staff2faService(Staff2faConfig config) {
        this.config = config;
    }

    /**
     * Update the config reference (e.g. on reload).
     */
    public void setConfig(Staff2faConfig config) {
        this.config = config;
    }

    /**
     * Check whether 2FA is enabled in the config.
     */
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    /**
     * Check whether a staff member is currently authenticated.
     *
     * @param uuid The staff member's UUID
     * @return {@code true} if authenticated or if 2FA is disabled
     */
    public boolean isAuthenticated(UUID uuid) {
        if (!isEnabled()) {
            return true;
        }
        AuthState state = authStates.get(uuid);
        return state == AuthState.AUTHENTICATED;
    }

    /**
     * Called when a staff member joins the server.
     * <p>
     * If 2FA is disabled the player is immediately authenticated.
     * Otherwise the player is placed in {@link AuthState#PENDING} and
     * the first sync response will determine whether their backend
     * session is still valid.
     *
     * @param uuid The staff member's UUID
     */
    public void onStaffJoin(UUID uuid) {
        if (!isEnabled()) {
            authStates.put(uuid, AuthState.AUTHENTICATED);
            return;
        }

        authStates.put(uuid, AuthState.PENDING);
    }

    /**
     * Handle a successful verification for a staff member.
     * Sets the player to {@link AuthState#AUTHENTICATED}.
     *
     * @param uuid The staff member's UUID
     */
    public void handleVerification(UUID uuid) {
        authStates.put(uuid, AuthState.AUTHENTICATED);
        notifiedPending.remove(uuid);
    }

    /**
     * Mark a PENDING player as having been notified about needing to verify.
     *
     * @return {@code true} if this is the first notification (was not already notified)
     */
    public boolean markNotified(UUID uuid) {
        return notifiedPending.add(uuid);
    }

    /**
     * Clean up state when a player disconnects.
     *
     * @param uuid The staff member's UUID
     */
    public void removePlayer(UUID uuid) {
        authStates.remove(uuid);
        notifiedPending.remove(uuid);
    }

    /**
     * Get the current auth state for a player (may be {@code null} if not tracked).
     */
    public AuthState getAuthState(UUID uuid) {
        return authStates.get(uuid);
    }
}
