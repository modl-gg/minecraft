package gg.modl.minecraft.velocity;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class JoinListener {

    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final Logger logger;
    private final ChatMessageCache chatMessageCache;
    private final Platform platform;
    private final SyncService syncService;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final boolean debugMode;
    private final gg.modl.minecraft.core.service.StaffChatService staffChatService;
    private final gg.modl.minecraft.core.service.ChatManagementService chatManagementService;
    private final gg.modl.minecraft.core.service.MaintenanceService maintenanceService;
    private final gg.modl.minecraft.core.service.FreezeService freezeService;
    private final gg.modl.minecraft.core.service.NetworkChatInterceptService networkChatInterceptService;
    private final gg.modl.minecraft.core.service.Staff2faService staff2faService;

    /**
     * Get the current HTTP client from the holder.
     */
    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        // Start IP lookup and skin hash fetch in parallel (non-blocking)
        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getPlayer().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> null);

        // Don't block login on IP lookup - use what's immediately available
        JsonObject ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception e) {
            // Continue without - backend will request IP lookup via pendingIpLookups
        }

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getUsername(),
                ipAddress,
                skinHash,
                ipInfo,
                platform.getServerName()
        );

        try {
            // Check for active punishments and prevent login if banned
            // Note: Velocity LoginEvent is synchronous and requires immediate decision
            CompletableFuture<PlayerLoginResponse> loginFuture = getHttpClient().playerLogin(request);
            PlayerLoginResponse response = loginFuture.get(5, TimeUnit.SECONDS); // 5 second timeout

            if (debugMode) {
                logger.info(String.format("Login response for %s: status=%d, punishments=%s",
                        event.getPlayer().getUsername(),
                        response.getStatus(),
                        response.getActivePunishments()));

                if (response.getActivePunishments() != null) {
                    for (SimplePunishment p : response.getActivePunishments()) {
                        logger.info(String.format("Punishment: type='%s', isBan=%s, isMute=%s, started=%s, id=%s",
                                p.getType(), p.isBan(), p.isMute(), p.isStarted(), p.getId()));
                    }
                }

                logger.info(String.format("Login response for %s: hasBan=%s, hasMute=%s",
                        event.getPlayer().getUsername(),
                        response.hasActiveBan(),
                        response.hasActiveMute()));
            }

            // Handle pending IP lookups requested by the backend
            ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getPlayer().getUniqueId().toString(), ipAddress, ipInfoFuture, java.util.logging.Logger.getLogger(JoinListener.class.getName()));

            if (response.hasActiveBan()) {
                SimplePunishment ban = response.getActiveBan();
                String banText = PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN);
                Component kickMessage = Colors.get(banText);
                event.setResult(ResultedEvent.ComponentResult.denied(kickMessage));

                if (debugMode) {
                    logger.info(String.format("Denied login for %s due to active ban: %s",
                            event.getPlayer().getUsername(), ban.getDescription()));
                }

                // Acknowledge ban enforcement if it wasn't started yet
                if (!ban.isStarted()) {
                    ListenerHelper.acknowledgeBanEnforcement(getHttpClient(), ban, event.getPlayer().getUniqueId().toString(), debugMode, java.util.logging.Logger.getLogger(JoinListener.class.getName()));
                }
            } else if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
                // Kick the player and immediately execute stat wipe commands via TCP bridge
                Component kickMessage = Colors.get(localeManager.getMessage("stat_wipe.kick_message"));
                event.setResult(ResultedEvent.ComponentResult.denied(kickMessage));
                for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) {
                    syncService.executeStatWipeFromLogin(statWipe);
                }
            } else if (maintenanceService.isEnabled() && !maintenanceService.canJoin(event.getPlayer().getUniqueId(), cache)) {
                // Maintenance mode check
                Component maintenanceMessage = Colors.get(localeManager.getMessage("maintenance.login_denied"));
                event.setResult(ResultedEvent.ComponentResult.denied(maintenanceMessage));
            } else {
                // Cache active mute if present
                if (response.hasActiveMute()) {
                    SimplePunishment mute = response.getActiveMute();
                    cache.cacheMute(event.getPlayer().getUniqueId(), mute);
                    if (debugMode) {
                        logger.info(String.format("Cached active mute for %s: %s",
                                event.getPlayer().getUsername(), mute.getDescription()));
                    }
                }

                // Process pending notifications from login response
                if (response.hasNotifications()) {
                    for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                        // Convert map to PlayerNotification and cache for delivery on join
                        SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(JoinListener.class.getName()));
                        if (notification != null) {
                            // Cache notification for immediate delivery on post-login
                            cache.cacheNotification(event.getPlayer().getUniqueId(), notification);
                        }
                    }
                }

                event.setResult(ResultedEvent.ComponentResult.allowed());

                if (debugMode) {
                    logger.info(String.format("Allowed login for %s", event.getPlayer().getUsername()));
                }
            }
        } catch (PanelUnavailableException e) {
            // Panel is restarting (502 error) - deny login for safety to prevent banned players from connecting
            logger.warn(String.format("Panel 502 during login check for %s - blocking login for safety",
                    event.getPlayer().getUsername()));
            Component errorMessage = Component.text("Unable to verify ban status. Login temporarily restricted for safety.")
                    .color(NamedTextColor.RED);
            event.setResult(ResultedEvent.ComponentResult.denied(errorMessage));
        } catch (java.util.concurrent.TimeoutException e) {
            // Login check timed out - deny for safety
            logger.warn(String.format("Login check timed out for %s - blocking login for safety",
                    event.getPlayer().getUsername()));
            Component errorMessage = Component.text("Login verification timed out. Please try again.")
                    .color(NamedTextColor.RED);
            event.setResult(ResultedEvent.ComponentResult.denied(errorMessage));
        } catch (Exception e) {
            // On other errors, allow login but log warning
            logger.error("Failed to check punishments for " + event.getPlayer().getUsername() +
                        " - allowing login as fallback", e);
            event.setResult(ResultedEvent.ComponentResult.allowed());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // 2FA: register staff as pending — actual auth check deferred to sync
        if (staff2faService != null && staff2faService.isEnabled() && PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            staff2faService.onStaffJoin(event.getPlayer().getUniqueId());
        }

        // Staff join notification (only if 2FA verified or not required — deferred to sync when 2FA is enabled)
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(event.getPlayer().getUniqueId()))) {
            String inGameName = event.getPlayer().getUsername();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.join", java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
        }

        // Cache skin texture from native Velocity API
        String texture = platform.getPlayerSkinTexture(event.getPlayer().getUniqueId());
        if (texture != null) {
            cache.cacheSkinTexture(event.getPlayer().getUniqueId(), texture);
        }

        // Deliver pending notifications
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        // Compute session duration BEFORE marking offline (which clears join time)
        long sessionDuration = cache.getSessionDuration(event.getPlayer().getUniqueId());
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(event.getPlayer().getUniqueId().toString(), sessionDuration);

        getHttpClient().playerDisconnect(request);

        // Staff leave notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String inGameName = event.getPlayer().getUsername();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.leave", java.util.Map.of("staff", panelName, "in-game-name", inGameName)));
            getHttpClient().reportStaffDisconnect(event.getPlayer().getUniqueId().toString(), sessionDuration);
        }

        // Freeze logout notification
        if (freezeService.isFrozen(event.getPlayer().getUniqueId())) {
            platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification", java.util.Map.of("player", event.getPlayer().getUsername())));
            freezeService.removePlayer(event.getPlayer().getUniqueId());
        }

        // Mark player as offline
        cache.setOffline(event.getPlayer().getUniqueId());

        // Clean up staff tools state
        staffChatService.removePlayer(event.getPlayer().getUniqueId());
        chatManagementService.removePlayer(event.getPlayer().getUniqueId());
        networkChatInterceptService.removePlayer(event.getPlayer().getUniqueId());
        if (staff2faService != null) staff2faService.removePlayer(event.getPlayer().getUniqueId());

        // Remove player from punishment cache
        cache.removePlayer(event.getPlayer().getUniqueId());

        // Remove player from chat message cache
        chatMessageCache.removePlayer(event.getPlayer().getUniqueId().toString());

        // Clear any pending chat input prompts
        gg.modl.minecraft.core.impl.menus.util.ChatInputManager.clearOnDisconnect(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        getHttpClient().updatePlayerServer(event.getPlayer().getUniqueId().toString(), serverName)
                .exceptionally(throwable -> {
                    logger.warn("Failed to update server for {}: {}", event.getPlayer().getUsername(), throwable.getMessage());
                    return null;
                });

        // Staff server switch notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String inGameName = event.getPlayer().getUsername();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.switch", java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", serverName)));
        }
    }

    public Cache getPunishmentCache() {
        return cache;
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
    
}
