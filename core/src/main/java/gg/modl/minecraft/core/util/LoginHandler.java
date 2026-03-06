package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.sync.SyncService;

import java.util.Map;
import java.util.UUID;

/**
 * Shared login response processing logic used by all platforms.
 * Each platform calls the API with its own async pattern, then delegates
 * the allow/deny decision to this handler.
 */
public final class LoginHandler {
    public sealed interface LoginResult {
        record Allowed(PlayerLoginResponse response) implements LoginResult {}
        record Denied(String message) implements LoginResult {}
    }

    /**
     * Processes a successful login response into an allow/deny decision.
     * Checks active bans, pending stat wipes, and maintenance mode.
     */
    public static LoginResult processLoginResponse(
            PlayerLoginResponse response, UUID playerUuid,
            gg.modl.minecraft.api.http.ModlHttpClient httpClient,
            LocaleManager localeManager, SyncService syncService,
            MaintenanceService maintenanceService, Cache cache,
            boolean debugMode, PluginLogger logger) {

        if (response.hasActiveBan()) {
            SimplePunishment ban = response.getActiveBan();
            String message = PunishmentMessages.formatBanMessage(ban, localeManager, PunishmentMessages.MessageContext.LOGIN);
            if (!ban.isStarted()) {
                ListenerHelper.acknowledgeBanEnforcement(
                        httpClient, ban, playerUuid.toString(), debugMode, logger);
            }
            return new LoginResult.Denied(message);
        }

        if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
            for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) {
                syncService.executeStatWipeFromLogin(statWipe);
            }
            return new LoginResult.Denied(localeManager.getMessage("stat_wipe.kick_message"));
        }

        if (maintenanceService.isEnabled() && !maintenanceService.canJoin(playerUuid, cache)) {
            return new LoginResult.Denied(localeManager.getMessage("maintenance.login_denied"));
        }

        return new LoginResult.Allowed(response);
    }

    /**
     * Determines the login result when the API call threw an exception.
     * PanelUnavailableException and TimeoutException block login for safety;
     * other errors allow login to prevent false kicks.
     */
    public static LoginResult handleLoginError(Exception error) {
        Throwable cause = error;
        if (error instanceof java.util.concurrent.ExecutionException && error.getCause() != null) {
            cause = error.getCause();
        }

        if (cause instanceof PanelUnavailableException) {
            return new LoginResult.Denied("Unable to verify ban status. Login temporarily restricted for safety.");
        }

        if (cause instanceof java.util.concurrent.TimeoutException) {
            return new LoginResult.Denied("Login verification timed out. Please try again.");
        }

        // Allow login on other errors to prevent false kicks
        return null;
    }

    /**
     * Caches mute and notification data from a login response.
     * Called after a successful (allowed) login on all platforms.
     * Notifications are delivered later by {@code SyncService.deliverPendingNotifications}
     * (triggered from {@code ListenerHelper.handlePlayerJoin}).
     */
    public static void cacheLoginData(
            UUID uuid, PlayerLoginResponse response,
            Cache cache, PluginLogger logger) {

        if (response == null) return;

        if (response.hasActiveMute()) {
            cache.cacheMute(uuid, response.getActiveMute());
        }

        if (response.hasNotifications()) {
            for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                SyncResponse.PlayerNotification notification =
                        ListenerHelper.mapToPlayerNotification(notificationData, logger);
                if (notification != null) {
                    cache.cacheNotification(uuid, notification);
                }
            }
        }
    }
}
