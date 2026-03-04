package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.config.Staff2faConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing staff two-factor authentication.
 * <p>
 * When enabled, staff members must verify their identity after joining.
 * IP addresses are cached with a configurable TTL so that staff connecting
 * from a recently-verified IP are auto-authenticated.
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

    /** UUID -> (IP -> timestamp of last successful verification). */
    private final Map<UUID, Map<String, Long>> ipAuthHistory = new ConcurrentHashMap<>();

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
     * Otherwise the player's IP is checked against the auth history;
     * if the IP was verified within the configured TTL the player is
     * auto-authenticated, otherwise they are placed in {@link AuthState#PENDING}.
     *
     * @param uuid The staff member's UUID
     * @param ip   The IP address the player connected from
     */
    public void onStaffJoin(UUID uuid, String ip) {
        if (!isEnabled()) {
            authStates.put(uuid, AuthState.AUTHENTICATED);
            return;
        }

        // Check IP history for auto-auth
        if (ip != null) {
            Map<String, Long> history = ipAuthHistory.get(uuid);
            if (history != null) {
                Long lastAuth = history.get(ip);
                if (lastAuth != null) {
                    long elapsedDays = (System.currentTimeMillis() - lastAuth) / (1000L * 60 * 60 * 24);
                    if (elapsedDays < config.getIpTtlDays()) {
                        authStates.put(uuid, AuthState.AUTHENTICATED);
                        return;
                    }
                }
            }
        }

        // IP not recently verified – require verification
        authStates.put(uuid, AuthState.PENDING);
    }

    /**
     * Handle a successful verification for a staff member.
     * Sets the player to {@link AuthState#AUTHENTICATED} and records
     * the IP in the auth history for future auto-auth.
     *
     * @param uuid The staff member's UUID
     * @param ip   The IP address verified from
     */
    public void handleVerification(UUID uuid, String ip) {
        authStates.put(uuid, AuthState.AUTHENTICATED);

        if (ip != null) {
            ipAuthHistory
                    .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(ip, System.currentTimeMillis());
        }
    }

    /**
     * Clean up state when a player disconnects.
     *
     * @param uuid The staff member's UUID
     */
    public void removePlayer(UUID uuid) {
        authStates.remove(uuid);
    }

    /**
     * Get the current auth state for a player (may be {@code null} if not tracked).
     */
    public AuthState getAuthState(UUID uuid) {
        return authStates.get(uuid);
    }
}
