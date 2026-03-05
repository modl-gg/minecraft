package gg.modl.minecraft.spigot;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
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
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
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
    private final gg.modl.minecraft.core.service.StaffChatService staffChatService;
    private final gg.modl.minecraft.core.service.ChatManagementService chatManagementService;
    private final gg.modl.minecraft.core.service.MaintenanceService maintenanceService;
    private final gg.modl.minecraft.core.service.FreezeService freezeService;
    private final gg.modl.minecraft.core.service.NetworkChatInterceptService networkChatInterceptService;
    private final gg.modl.minecraft.core.service.ChatCommandLogService chatCommandLogService;
    private final gg.modl.minecraft.core.service.Staff2faService staff2faService;
    private final gg.modl.minecraft.core.config.StaffChatConfig staffChatConfig;

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
        } else if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(localeManager.getMessage("stat_wipe.kick_message"));
            for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) {
                syncService.executeStatWipeFromLogin(statWipe);
            }
        }

        // Maintenance mode check
        if (maintenanceService.isEnabled() && !maintenanceService.canJoin(event.getPlayer().getUniqueId(), cache)) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(localeManager.getMessage("maintenance.login_denied"));
            return;
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // 2FA: register staff as pending — actual auth check deferred to sync
        if (staff2faService != null && staff2faService.isEnabled() && PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            staff2faService.onStaffJoin(event.getPlayer().getUniqueId());
        }

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

        // Cache skin texture from WebPlayer on pre-1.18.1 Spigot (if native API was unavailable)
        if (cache.getSkinTexture(event.getPlayer().getUniqueId()) == null) {
            WebPlayer.get(event.getPlayer().getUniqueId())
                .thenAccept(webPlayer -> {
                    if (webPlayer != null && webPlayer.valid() && webPlayer.textureValue() != null) {
                        cache.cacheSkinTexture(event.getPlayer().getUniqueId(), webPlayer.textureValue());
                    }
                })
                .exceptionally(throwable -> null);
        }

        // Use cached login response from pre-login instead of making another API call
        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(event.getPlayer().getUniqueId());
        PlayerLoginResponse response = cachedResult != null ? cachedResult.getResponse() : null;

        if (response != null) {
            // Cache mute status
            if (response.hasActiveMute()) {
                cache.cacheMute(event.getPlayer().getUniqueId(), response.getActiveMute());
            }

            // Process pending notifications from login response
            if (response.hasNotifications()) {
                for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                    SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(SpigotListener.class.getName()));
                    if (notification != null) {
                        syncService.deliverLoginNotification(event.getPlayer().getUniqueId(), notification);
                    }
                }
            }
        }

        // Staff join notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(event.getPlayer().getUniqueId()))) {
            String inGameName = event.getPlayer().getName();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.join", java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
        }

        // Also deliver any cached notifications (fallback)
        syncService.deliverPendingNotifications(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                getHttpClient(), cache, platform, localeManager, freezeService,
                staffChatService, chatManagementService, networkChatInterceptService,
                staff2faService, chatMessageCache);
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

        // Staff chat: if sender is in staff chat mode, redirect to staff chat
        if (staffChatService.isInStaffChat(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            String inGameName = event.getPlayer().getName();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            platform.staffBroadcast(staffChatConfig.formatMessage(inGameName, panelName, event.getMessage()));
            return;
        }

        // Staff chat prefix shortcut
        if (staffChatConfig.isEnabled() && event.getMessage().startsWith(staffChatConfig.getPrefix())
                && PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            event.setCancelled(true);
            String msg = event.getMessage().substring(staffChatConfig.getPrefix().length()).trim();
            if (!msg.isEmpty()) {
                String inGameName = event.getPlayer().getName();
                String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
                platform.staffBroadcast(staffChatConfig.formatMessage(inGameName, panelName, msg));
            }
            return;
        }

        // Chat management: chat disabled check
        boolean isStaff = PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache);
        if (!chatManagementService.canSendMessage(event.getPlayer().getUniqueId(), isStaff)) {
            event.setCancelled(true);
            if (!chatManagementService.isChatEnabled()) {
                event.getPlayer().sendMessage(localeManager.getMessage("chat_management.chat_disabled"));
            } else {
                int remaining = chatManagementService.getSlowModeRemaining(event.getPlayer().getUniqueId());
                event.getPlayer().sendMessage(localeManager.getMessage("chat_management.slow_mode_wait",
                        java.util.Map.of("seconds", String.valueOf(remaining))));
            }
            return;
        }

        if (cache.isMuted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PunishmentMessages.getMuteMessage(event.getPlayer().getUniqueId(), cache, localeManager));
            return;
        }

        // Freeze: redirect frozen player chat to staff
        if (freezeService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            platform.staffBroadcast(localeManager.getMessage("freeze.frozen_chat",
                    java.util.Map.of("player", event.getPlayer().getName(), "message", event.getMessage())));
            return;
        }

        // Log chat message
        chatCommandLogService.addChatMessage(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                event.getMessage(),
                platform.getServerName()
        );

        // Forward to interceptors
        for (java.util.UUID interceptor : networkChatInterceptService.getInterceptors()) {
            if (!interceptor.equals(event.getPlayer().getUniqueId())) {
                platform.sendMessage(interceptor, localeManager.getMessage("intercept.message",
                        java.util.Map.of("player", event.getPlayer().getName(), "message", event.getMessage())));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Freeze: block all commands for frozen players
        if (freezeService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(localeManager.getMessage("freeze.command_blocked"));
            return;
        }

        // Log command
        chatCommandLogService.addCommand(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getName(),
                event.getMessage(),
                platform.getServerName()
        );

        if (!cache.isMuted(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!MutedCommandUtil.isBlockedCommand(event.getMessage(), mutedCommands)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(PunishmentMessages.getMuteMessage(event.getPlayer().getUniqueId(), cache, localeManager));
    }

    public Cache getPunishmentCache() {
        return cache;
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
    }
    
}