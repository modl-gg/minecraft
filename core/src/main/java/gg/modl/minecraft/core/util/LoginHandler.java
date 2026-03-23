package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.service.sync.SyncService;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

public final class LoginHandler {
    private LoginHandler() {}
    public interface LoginResult {
        @Data @AllArgsConstructor final class Allowed implements LoginResult {
            private final PlayerLoginResponse response;
        }
        @Data @AllArgsConstructor final class Denied implements LoginResult {
            private final String message;
        }
    }

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

        return null;
    }

    public static void cacheLoginData(
            UUID uuid, PlayerLoginResponse response,
            Cache cache, PluginLogger logger) {

        if (response == null) return;

        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return;

        if (response.hasActiveMute()) {
            profile.setActiveMute(response.getActiveMute());
        }

        if (response.hasNotifications()) {
            for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                SyncResponse.PlayerNotification notification =
                        ListenerHelper.mapToPlayerNotification(notificationData, logger);
                if (notification != null) {
                    profile.addNotification(notification);
                }
            }
        }
    }
}
