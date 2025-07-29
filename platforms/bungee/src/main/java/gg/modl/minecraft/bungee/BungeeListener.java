package gg.modl.minecraft.bungee;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.PunishmentMessages;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@RequiredArgsConstructor
public class BungeeListener implements Listener {

    private final BungeePlatform platform;
    private final Cache cache;
    private final ModlHttpClient httpClient;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final String panelUrl;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        // Extract clean IP address from socket address (removes port)
        String socketAddress = event.getConnection().getSocketAddress().toString();
        String ipAddress = socketAddress.startsWith("/") ? socketAddress.substring(1) : socketAddress;
        if (ipAddress.contains(":")) {
            ipAddress = ipAddress.substring(0, ipAddress.indexOf(":"));
        }
        
        // Get IP information asynchronously but wait for it (with timeout)
        JsonObject ipInfo = null;
        try {
            CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
            ipInfo = ipInfoFuture.get(3, TimeUnit.SECONDS); // 3 second timeout
        } catch (Exception e) {
            platform.getLogger().warning("Failed to get IP info for " + ipAddress + " within timeout: " + e.getMessage());
            // Continue without IP info
        }
        
        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getConnection().getUniqueId().toString(),
                event.getConnection().getName(),
                ipAddress,
                null,
                ipInfo,
                platform.getServerName()
        );

        try {
            // Check for active punishments and prevent login if banned (synchronous)
            CompletableFuture<PlayerLoginResponse> loginFuture = httpClient.playerLogin(request);
            PlayerLoginResponse response = loginFuture.join(); // Block until response
            
            if (response.hasActiveBan()) {
                SimplePunishment ban = response.getActiveBan();
                String banText = PunishmentMessages.formatBanMessage(ban);
                TextComponent kickMessage = new TextComponent(banText);
                event.setCancelReason(kickMessage);
                event.setCancelled(true);
                
                // Acknowledge ban enforcement if it wasn't started yet
                if (!ban.isStarted()) {
                    acknowledgeBanEnforcement(ban, event.getConnection().getUniqueId().toString());
                }
            }
        } catch (PanelUnavailableException e) {
            // Panel is restarting (502 error) - deny login for safety to prevent banned players from connecting
            platform.getLogger().warning("Panel 502 during login check for " + event.getConnection().getName() + " - blocking login for safety");
            TextComponent errorMessage = new TextComponent("Unable to verify ban status. Login temporarily restricted for safety.");
            event.setCancelReason(errorMessage);
            event.setCancelled(true);
        } catch (Exception e) {
            // On other errors, allow login but log warning
            platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Cache mute status after successful join
        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                event.getPlayer().getSocketAddress().toString(),
                null,
                null,
                platform.getServerName()
        );
        
        httpClient.playerLogin(request).thenAccept(response -> {
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
        
        // Also deliver any cached notifications (fallback)
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(event.getPlayer().getUniqueId().toString());

        httpClient.playerDisconnect(request);
        
        // Remove player from punishment cache
        cache.removePlayer(event.getPlayer().getUniqueId());
        
        // Remove player from chat message cache
        chatMessageCache.removePlayer(event.getPlayer().getUniqueId().toString());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (event.isCommand() || event.getSender() == null) {
            return; // Ignore commands and non-player senders
        }

        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();
        
        // Cache the chat message first (before potentially cancelling)
        // Use the player's current server name for cross-server compatibility
        String serverName = sender.getServer() != null ? sender.getServer().getInfo().getName() : "unknown";
        chatMessageCache.addMessage(
            serverName,
            sender.getUniqueId().toString(),
            sender.getName(),
            event.getMessage()
        );
        
        if (cache.isMuted(sender.getUniqueId())) {
            // Cancel the chat event
            event.setCancelled(true);
            
            // Get cached mute and send message to player
            Cache.CachedPlayerData data = cache.getCache().get(sender.getUniqueId());
            if (data != null) {
                String muteMessage;
                if (data.getSimpleMute() != null) {
                    muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute());
                } else if (data.getMute() != null) {
                    // Fallback to old punishment format
                    muteMessage = formatMuteMessage(data.getMute());
                } else {
                    muteMessage = "§cYou are muted!";
                }
                // Handle both escaped newlines and literal \n sequences
                String formattedMessage = muteMessage.replace("\\n", "\n").replace("\\\\n", "\n");
                sender.sendMessage(new TextComponent(formattedMessage));
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
            
            httpClient.acknowledgePunishment(request).thenAccept(response -> {
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
                notification.setData((Map<String, Object>) nestedData);
            }
            
            return notification;
        } catch (Exception e) {
            platform.getLogger().warning("Failed to convert notification data: " + e.getMessage());
            return null;
        }
    }
}