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
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.MutedCommandUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;

@RequiredArgsConstructor
public class BungeeListener implements Listener {

    private final BungeePlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final boolean debugMode;
    private final List<String> mutedCommands;

    /**
     * Get the current HTTP client from the holder.
     */
    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        // Extract clean IP address from socket address
        String ipAddress = extractIpAddress(event.getConnection().getSocketAddress());

        // Start IP lookup and skin hash fetch in parallel (non-blocking)
        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getConnection().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> null);

        // Wait briefly for both to complete, but don't block login
        JsonObject ipInfo = null;
        String skinHash = null;
        try {
            // Give parallel tasks a short window before proceeding
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception e) {
            // Continue without - backend will request IP lookup via pendingIpLookups
        }

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getConnection().getUniqueId().toString(),
                event.getConnection().getName(),
                ipAddress,
                skinHash,
                ipInfo,
                platform.getServerName()
        );

        try {
            // Check for active punishments and prevent login if banned
            CompletableFuture<PlayerLoginResponse> loginFuture = getHttpClient().playerLogin(request);
            PlayerLoginResponse response = loginFuture.get(5, TimeUnit.SECONDS); // 5 second timeout
            
            // Handle pending IP lookups requested by the backend
            handlePendingIpLookups(response, event.getConnection().getUniqueId().toString(), ipAddress, ipInfoFuture);

            if (response.hasActiveBan()) {
                SimplePunishment ban = response.getActiveBan();
                String banText = PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN);
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
        } catch (java.util.concurrent.TimeoutException e) {
            // Login check timed out - deny for safety
            platform.getLogger().warning("Login check timed out for " + event.getConnection().getName() + " - blocking login for safety");
            TextComponent errorMessage = new TextComponent("Login verification timed out. Please try again.");
            event.setCancelReason(errorMessage);
            event.setCancelled(true);
        } catch (Exception e) {
            // On other errors, allow login but log warning
            platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // Get player skin hash for punishment tracking
        String skinHash = null;
        try {
            WebPlayer webPlayer = WebPlayer.get(event.getPlayer().getUniqueId()).get(3, TimeUnit.SECONDS);
            if (webPlayer != null && webPlayer.valid()) {
                skinHash = webPlayer.skin();
            }
        } catch (Exception e) {
            platform.getLogger().warning("Failed to get skin hash for " + event.getPlayer().getName() + ": " + e.getMessage());
            // Continue without skin hash
        }
        
        // Extract clean IP address
        String ipAddress = extractIpAddress(event.getPlayer().getSocketAddress());

        // Cache mute status after successful join
        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                ipAddress,
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
        
        // Also deliver any cached notifications (fallback)
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        // Compute session duration BEFORE marking offline (which clears join time)
        long sessionDuration = cache.getSessionDuration(event.getPlayer().getUniqueId());
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(event.getPlayer().getUniqueId().toString(), sessionDuration);

        getHttpClient().playerDisconnect(request);

        // Mark player as offline
        cache.setOffline(event.getPlayer().getUniqueId());

        // Remove player from punishment cache
        cache.removePlayer(event.getPlayer().getUniqueId());

        // Remove player from chat message cache
        chatMessageCache.removePlayer(event.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        String serverName = platform.getPlayerServer(event.getPlayer().getUniqueId());
        getHttpClient().updatePlayerServer(event.getPlayer().getUniqueId().toString(), serverName)
                .exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to update server for " + event.getPlayer().getName() + ": " + throwable.getMessage());
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (event.getSender() == null) {
            return;
        }

        // Handle commands: block muted commands, skip all other commands
        if (event.isCommand()) {
            if (!(event.getSender() instanceof ProxiedPlayer)) {
                return;
            }
            ProxiedPlayer sender = (ProxiedPlayer) event.getSender();
            if (cache.isMuted(sender.getUniqueId()) && MutedCommandUtil.isBlockedCommand(event.getMessage(), mutedCommands)) {
                event.setCancelled(true);
                sendMuteMessage(sender);
            }
            return;
        }

        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();

        // Check for pending menu chat input FIRST
        if (ChatInputManager.handleChat(sender.getUniqueId(), event.getMessage())) {
            event.setCancelled(true);
            return;
        }

        // Cache the chat message (before potentially cancelling for mute)
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
            sendMuteMessage(sender);
        }
    }

    private void sendMuteMessage(ProxiedPlayer sender) {
        Cache.CachedPlayerData data = cache.getCache().get(sender.getUniqueId());
        if (data != null) {
            String muteMessage;
            if (data.getSimpleMute() != null) {
                muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
            } else if (data.getMute() != null) {
                muteMessage = formatMuteMessage(data.getMute());
            } else {
                muteMessage = "§cYou are muted!";
            }
            // Handle both escaped newlines and literal \n sequences
            String formattedMessage = muteMessage.replace("\\n", "\n").replace("\\\\n", "\n");
            sender.sendMessage(new TextComponent(formattedMessage));
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
                if (debugMode) {
                    platform.getLogger().info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
                }
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
     * Reuses the original ipInfoFuture for the player's current IP to avoid redundant API calls.
     */
    private void handlePendingIpLookups(gg.modl.minecraft.api.http.response.PlayerLoginResponse response, String minecraftUUID, String originalIp, CompletableFuture<JsonObject> originalIpInfoFuture) {
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

    /**
     * Extract clean IP address from a socket address.
     * Removes leading slash and port number.
     */
    private String extractIpAddress(java.net.SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
        }
        // Fallback: parse from string representation
        String addr = socketAddress.toString();
        if (addr.startsWith("/")) {
            addr = addr.substring(1);
        }
        if (addr.contains(":")) {
            addr = addr.substring(0, addr.indexOf(":"));
        }
        return addr;
    }
}