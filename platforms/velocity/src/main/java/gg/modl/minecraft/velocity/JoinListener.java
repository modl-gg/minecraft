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
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
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
    private final String panelUrl;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final boolean debugMode;

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
            handlePendingIpLookups(response, event.getPlayer().getUniqueId().toString(), ipAddress, ipInfoFuture);

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
                    acknowledgeBanEnforcement(ban, event.getPlayer().getUniqueId().toString());
                }
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
                        SyncResponse.PlayerNotification notification = mapToPlayerNotification(notificationData);
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

        // Deliver pending notifications
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(event.getPlayer().getUniqueId().toString());

        getHttpClient().playerDisconnect(request);

        // Mark player as offline
        cache.setOffline(event.getPlayer().getUniqueId());

        // Remove player from punishment cache
        cache.removePlayer(event.getPlayer().getUniqueId());

        // Remove player from chat message cache
        chatMessageCache.removePlayer(event.getPlayer().getUniqueId().toString());
    }
    
    
    public Cache getPunishmentCache() {
        return cache;
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
    
    /**
     * Acknowledge that a ban was successfully enforced (player denied login)
     */
    private void acknowledgeBanEnforcement(SimplePunishment ban, String playerUuid) {
        try {
            PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                    ban.getId(),
                    playerUuid,
                    java.time.Instant.now().toString(),
                    true, // success
                    null // no error message
            );
            
            getHttpClient().acknowledgePunishment(request).thenAccept(response -> {
                if (debugMode) {
                    logger.info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
                }
            }).exceptionally(throwable -> {
                logger.error("Failed to acknowledge ban enforcement for punishment " + ban.getId(), throwable);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error acknowledging ban enforcement for punishment " + ban.getId(), e);
        }
    }
    
    /**
     * Handle pending IP lookups requested by the backend.
     * Reuses the original ipInfoFuture for the player's current IP to avoid redundant API calls.
     */
    private void handlePendingIpLookups(PlayerLoginResponse response, String minecraftUUID, String originalIp, CompletableFuture<JsonObject> originalIpInfoFuture) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) {
            return;
        }
        for (String ip : response.getPendingIpLookups()) {
            CompletableFuture<JsonObject> ipInfoFuture = ip.equals(originalIp) && originalIpInfoFuture != null
                    ? originalIpInfoFuture
                    : IpApiClient.getIpInfo(ip);
            ipInfoFuture.thenAccept(ipInfo -> {
                if (ipInfo != null && ipInfo.has("status") && "success".equals(ipInfo.get("status").getAsString())) {
                    getHttpClient().submitIpInfo(
                            minecraftUUID,
                            ip,
                            ipInfo.has("countryCode") ? ipInfo.get("countryCode").getAsString() : null,
                            ipInfo.has("regionName") ? ipInfo.get("regionName").getAsString() : null,
                            ipInfo.has("as") ? ipInfo.get("as").getAsString() : null,
                            ipInfo.has("proxy") && ipInfo.get("proxy").getAsBoolean(),
                            ipInfo.has("hosting") && ipInfo.get("hosting").getAsBoolean()
                    ).exceptionally(throwable -> {
                        logger.warn("Failed to submit IP info for {}: {}", ip, throwable.getMessage());
                        return null;
                    });
                }
            }).exceptionally(throwable -> {
                logger.warn("Failed to lookup IP {}: {}", ip, throwable.getMessage());
                return null;
            });
        }
    }

    /**
     * Convert a map from the login response to a PlayerNotification object
     */
    private SyncResponse.PlayerNotification mapToPlayerNotification(Map<String, Object> data) {
        try {
            SyncResponse.PlayerNotification notification = new SyncResponse.PlayerNotification();
            notification.setId((String) data.get("id"));
            notification.setMessage((String) data.get("message"));
            notification.setType((String) data.get("type"));
            
            if (data.get("timestamp") instanceof Number) {
                notification.setTimestamp(((Number) data.get("timestamp")).longValue());
            }
            
            notification.setTargetPlayerUuid((String) data.get("targetPlayerUuid"));
            
            // Handle nested data map
            Object nestedData = data.get("data");
            if (nestedData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) nestedData;
                notification.setData(dataMap);
            }
            
            return notification;
        } catch (Exception e) {
            logger.warn("Failed to convert notification data: " + e.getMessage());
            return null;
        }
    }
}
