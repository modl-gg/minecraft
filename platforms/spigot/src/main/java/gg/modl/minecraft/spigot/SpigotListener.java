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
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.MutedCommandUtil;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
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
    private final gg.modl.minecraft.core.locale.LocaleManager localeManager;
    private final LoginCache loginCache;
    private final boolean debugMode;
    private final List<String> mutedCommands;

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
                        ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getUniqueId().toString(), ipAddress, CompletableFuture.completedFuture(ipInfo), java.util.logging.Logger.getLogger(SpigotListener.class.getName()));
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
                ListenerHelper.acknowledgeBanEnforcement(getHttpClient(), ban, event.getPlayer().getUniqueId().toString(), debugMode, java.util.logging.Logger.getLogger(SpigotListener.class.getName()));
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // Cache skin texture - try native API first (1.18.1+), falls back to WebPlayer.get() chain below
        String nativeTexture = platform.getPlayerSkinTexture(event.getPlayer().getUniqueId());
        if (nativeTexture != null) {
            cache.cacheSkinTexture(event.getPlayer().getUniqueId(), nativeTexture);
        }

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
                    // On pre-1.18.1 Spigot, cache texture from WebPlayer if native API was unavailable
                    if (cache.getSkinTexture(event.getPlayer().getUniqueId()) == null && webPlayer.textureValue() != null) {
                        cache.cacheSkinTexture(event.getPlayer().getUniqueId(), webPlayer.textureValue());
                    }
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
                            SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(SpigotListener.class.getName()));
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
                            SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(SpigotListener.class.getName()));
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

        // Clear any pending chat input prompts
        gg.modl.minecraft.core.impl.menus.util.ChatInputManager.clearOnDisconnect(event.getPlayer().getUniqueId());
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
                    muteMessage = PunishmentMessages.formatLegacyMuteMessage(data.getMute());
                } else {
                    muteMessage = ChatColor.RED + "You are muted!";
                }
                event.getPlayer().sendMessage(muteMessage);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!cache.isMuted(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!MutedCommandUtil.isBlockedCommand(event.getMessage(), mutedCommands)) {
            return;
        }

        event.setCancelled(true);

        // Send mute message to player
        Cache.CachedPlayerData data = cache.getCache().get(event.getPlayer().getUniqueId());
        if (data != null) {
            String muteMessage;
            if (data.getSimpleMute() != null) {
                muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
            } else if (data.getMute() != null) {
                muteMessage = PunishmentMessages.formatLegacyMuteMessage(data.getMute());
            } else {
                muteMessage = ChatColor.RED + "You are muted!";
            }
            event.getPlayer().sendMessage(muteMessage);
        }
    }

    public Cache getPunishmentCache() {
        return cache;
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
    
}