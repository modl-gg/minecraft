package gg.modl.minecraft.spigot;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.LoginCache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SpigotListener implements Listener {

    private final SpigotPlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final String panelUrl;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final LoginCache loginCache;

    /**
     * Get the current HTTP client from the holder.
     */
    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    /**
     * Async pre-login event - performs all HTTP operations on background thread
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String ipAddress = event.getAddress().getHostAddress();

        // Check cache first to avoid repeated API calls
        LoginCache.CachedLoginResult cached = loginCache.getCachedLoginResult(event.getUniqueId());
        if (cached != null) {
            platform.getLogger().fine("Using cached login result for " + event.getName());
            loginCache.storePreLoginResult(event.getUniqueId(),
                new LoginCache.PreLoginResult(cached.getResponse(), cached.getIpInfo(), cached.getSkinHash()));
            return;
        }

        // Perform all async operations
        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<WebPlayer> webPlayerFuture = WebPlayer.get(event.getUniqueId());

        // Combine both futures and process results
        CompletableFuture<Void> combinedFuture = ipInfoFuture
            .thenCombine(webPlayerFuture, (ipInfo, webPlayer) -> {
                // Extract skin hash from WebPlayer
                String skinHash = null;
                if (webPlayer != null && webPlayer.valid()) {
                    skinHash = webPlayer.skin();
                }

                // Create login request
                PlayerLoginRequest request = new PlayerLoginRequest(
                        event.getUniqueId().toString(),
                        event.getName(),
                        ipAddress,
                        skinHash,
                        ipInfo,
                        platform.getServerName()
                );

                return new Object[] { request, ipInfo, skinHash };
            })
            .thenCompose(data -> {
                PlayerLoginRequest request = (PlayerLoginRequest) data[0];
                JsonObject ipInfo = (JsonObject) data[1];
                String skinHash = (String) data[2];

                // Perform login check
                return getHttpClient().playerLogin(request)
                    .thenAccept(response -> {
                        // Cache the result
                        loginCache.cacheLoginResult(event.getUniqueId(), response, ipInfo, skinHash);

                        // Store for sync event
                        loginCache.storePreLoginResult(event.getUniqueId(),
                            new LoginCache.PreLoginResult(response, ipInfo, skinHash));

                        // Handle pending IP lookups requested by the backend
                        handlePendingIpLookups(response, event.getUniqueId().toString());
                    });
            })
            .exceptionally(throwable -> {
                platform.getLogger().warning("Failed to check punishments for " + event.getName() + ": " + throwable.getMessage());

                // Store error result
                Exception error = throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable);
                loginCache.storePreLoginResult(event.getUniqueId(), new LoginCache.PreLoginResult(error));
                return null;
            });

        // Wait for completion with timeout (max 10 seconds)
        try {
            combinedFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            platform.getLogger().warning("Async pre-login timed out for " + event.getName() + ": " + e.getMessage());
            loginCache.storePreLoginResult(event.getUniqueId(), new LoginCache.PreLoginResult(e));
        }
    }

    /**
     * Sync login event - makes decision based on cached async results
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Get pre-login result from cache
        LoginCache.PreLoginResult preLoginResult = loginCache.getAndRemovePreLoginResult(event.getPlayer().getUniqueId());

        if (preLoginResult == null) {
            platform.getLogger().warning("No pre-login result found for " + event.getPlayer().getName() + " - allowing login as fallback");
            return;
        }

        if (preLoginResult.hasError()) {
            Exception error = preLoginResult.getError();
            if (error instanceof PanelUnavailableException) {
                // Panel is restarting (502 error) - deny login for safety
                platform.getLogger().warning("Panel 502 during login check for " + event.getPlayer().getName() + " - blocking login for safety");
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage("Unable to verify ban status. Login temporarily restricted for safety.");
            } else {
                platform.getLogger().severe("Failed to check punishments for " + event.getPlayer().getName() + ": " + error.getMessage());
                // Allow login on other errors to prevent false kicks
            }
            return;
        }

        if (!preLoginResult.isSuccess()) {
            platform.getLogger().warning("Invalid pre-login result for " + event.getPlayer().getName() + " - allowing login as fallback");
            return;
        }

        PlayerLoginResponse response = preLoginResult.getResponse();
        if (response.hasActiveBan()) {
            SimplePunishment ban = response.getActiveBan();
            String banMessage = PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN);
            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
            event.setKickMessage(banMessage);

            // Acknowledge ban enforcement if it wasn't started yet
            if (!ban.isStarted()) {
                acknowledgeBanEnforcement(ban, event.getPlayer().getUniqueId().toString());
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // Update chat message cache with player's server (for chat reports)
        chatMessageCache.updatePlayerServer(
            platform.getServerName(),
            event.getPlayer().getUniqueId().toString()
        );

        // Get skin hash asynchronously and then cache mute status
        WebPlayer.get(event.getPlayer().getUniqueId())
            .thenAccept(webPlayer -> {
                String skinHash = null;
                if (webPlayer != null && webPlayer.valid()) {
                    skinHash = webPlayer.skin();
                }

                // Cache mute status after successful join
                PlayerLoginRequest request = new PlayerLoginRequest(
                        event.getPlayer().getUniqueId().toString(),
                        event.getPlayer().getName(),
                        event.getPlayer().getAddress().getAddress().getHostAddress(),
                        skinHash,
                        null,
                        platform.getServerName()
                );

                getHttpClient().playerLogin(request).thenAccept(response -> {
                    if (response.hasActiveMute()) {
                        cache.cacheMute(event.getPlayer().getUniqueId(), response.getActiveMute());
                    }

                    // Process pending notifications from login response
                    if (response.hasNotifications()) {
                        for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                            // Convert map to PlayerNotification and deliver immediately
                            SyncResponse.PlayerNotification notification = mapToPlayerNotification(notificationData);
                            if (notification != null) {
                                syncService.deliverLoginNotification(event.getPlayer().getUniqueId(), notification);
                            }
                        }
                    }
                }).exceptionally(throwable -> {
                    platform.getLogger().severe("Failed to cache mute for " + event.getPlayer().getName() + ": " + throwable.getMessage());
                    return null;
                });
            })
            .exceptionally(throwable -> {
                platform.getLogger().warning("Failed to get skin hash for " + event.getPlayer().getName() + ": " + throwable.getMessage());

                // Continue without skin hash
                PlayerLoginRequest request = new PlayerLoginRequest(
                        event.getPlayer().getUniqueId().toString(),
                        event.getPlayer().getName(),
                        event.getPlayer().getAddress().getAddress().getHostAddress(),
                        null,
                        null,
                        platform.getServerName()
                );

                getHttpClient().playerLogin(request).thenAccept(response -> {
                    if (response.hasActiveMute()) {
                        cache.cacheMute(event.getPlayer().getUniqueId(), response.getActiveMute());
                    }

                    // Process pending notifications from login response
                    if (response.hasNotifications()) {
                        for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                            // Convert map to PlayerNotification and deliver immediately
                            SyncResponse.PlayerNotification notification = mapToPlayerNotification(notificationData);
                            if (notification != null) {
                                syncService.deliverLoginNotification(event.getPlayer().getUniqueId(), notification);
                            }
                        }
                    }
                }).exceptionally(innerThrowable -> {
                    platform.getLogger().severe("Failed to cache mute for " + event.getPlayer().getName() + ": " + innerThrowable.getMessage());
                    return null;
                });

                return null;
            });

        // Also deliver any cached notifications (fallback)
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(event.getPlayer().getUniqueId().toString());

        getHttpClient().playerDisconnect(request);

        // Mark player as offline
        cache.setOffline(event.getPlayer().getUniqueId());

        // Remove player from punishment cache
        cache.removePlayer(event.getPlayer().getUniqueId());

        // Remove player from chat message cache
        chatMessageCache.removePlayer(event.getPlayer().getUniqueId().toString());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Check for pending menu chat input FIRST
        if (ChatInputManager.handleChat(event.getPlayer().getUniqueId(), event.getMessage())) {
            event.setCancelled(true);
            return;
        }

        // Cache the chat message (before potentially cancelling for mute)
        chatMessageCache.addMessage(
            platform.getServerName(), // Server name for cross-platform compatibility
            event.getPlayer().getUniqueId().toString(),
            event.getPlayer().getName(),
            event.getMessage()
        );

        if (cache.isMuted(event.getPlayer().getUniqueId())) {
            // Cancel the chat event
            event.setCancelled(true);
            
            // Get cached mute and send message to player
            Cache.CachedPlayerData data = cache.getCache().get(event.getPlayer().getUniqueId());
            if (data != null) {
                String muteMessage;
                if (data.getSimpleMute() != null) {
                    muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
                } else if (data.getMute() != null) {
                    // Fallback to old punishment format
                    muteMessage = formatMuteMessage(data.getMute());
                } else {
                    muteMessage = ChatColor.RED + "You are muted!";
                }
                event.getPlayer().sendMessage(muteMessage);
            }
        }
    }
    
    /**
     * Format mute message for old punishment format (fallback)
     */
    private String formatMuteMessage(Punishment mute) {
        String reason = mute.getReason() != null ? mute.getReason() : "No reason provided";
        
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append("You are muted!\n");
        message.append(ChatColor.GRAY).append("Reason: ").append(ChatColor.WHITE).append(reason);
        
        if (mute.getExpires() != null) {
            long timeLeft = mute.getExpires().getTime() - System.currentTimeMillis();
            if (timeLeft > 0) {
                String timeString = PunishmentMessages.formatDuration(timeLeft);
                message.append("\n").append(ChatColor.GRAY).append("Time remaining: ")
                       .append(ChatColor.WHITE).append(timeString);
            }
        } else {
            message.append("\n").append(ChatColor.DARK_RED).append("This mute is permanent.");
        }
        
        return message.toString();
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
                platform.getLogger().info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
            }).exceptionally(throwable -> {
                platform.getLogger().severe("Failed to acknowledge ban enforcement for punishment " + ban.getId() + ": " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            platform.getLogger().severe("Error acknowledging ban enforcement for punishment " + ban.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle pending IP lookups requested by the backend.
     * The backend sends a list of IPs that need geo data (first-time IPs).
     * We look them up using the free ip-api.com and submit the results back.
     */
    private void handlePendingIpLookups(PlayerLoginResponse response, String minecraftUUID) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) {
            return;
        }
        for (String ip : response.getPendingIpLookups()) {
            IpApiClient.getIpInfo(ip).thenAccept(ipInfo -> {
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
                        platform.getLogger().warning("Failed to submit IP info for " + ip + ": " + throwable.getMessage());
                        return null;
                    });
                }
            }).exceptionally(throwable -> {
                platform.getLogger().warning("Failed to lookup IP " + ip + ": " + throwable.getMessage());
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
            platform.getLogger().warning("Failed to convert notification data: " + e.getMessage());
            return null;
        }
    }
}