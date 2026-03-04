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
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.MutedCommandUtil;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.StringUtil;
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
import net.md_5.bungee.api.plugin.Plugin;
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
    private final Plugin plugin;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        // Register async intent so BungeeCord waits for our check to complete
        // without blocking the Netty network thread
        event.registerIntent(plugin);

        CompletableFuture.runAsync(() -> {
            try {
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

                // Check for active punishments and prevent login if banned
                CompletableFuture<PlayerLoginResponse> loginFuture = getHttpClient().playerLogin(request);
                PlayerLoginResponse response = loginFuture.get(5, TimeUnit.SECONDS);

                // Handle pending IP lookups requested by the backend
                ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getConnection().getUniqueId().toString(), ipAddress, ipInfoFuture, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));

                if (response.hasActiveBan()) {
                    SimplePunishment ban = response.getActiveBan();
                    String banText = PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN);
                    TextComponent kickMessage = new TextComponent(banText);
                    event.setCancelReason(kickMessage);
                    event.setCancelled(true);

                    // Acknowledge ban enforcement if it wasn't started yet
                    if (!ban.isStarted()) {
                        ListenerHelper.acknowledgeBanEnforcement(getHttpClient(), ban, event.getConnection().getUniqueId().toString(), debugMode, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));
                    }
                } else if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
                    // Kick the player and immediately execute stat wipe commands via TCP bridge
                    event.setCancelReason(new TextComponent(localeManager.getMessage("stat_wipe.kick_message")));
                    event.setCancelled(true);
                    for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) {
                        syncService.executeStatWipeFromLogin(statWipe);
                    }
                }

                // Maintenance mode check
                if (!event.isCancelled() && maintenanceService.isEnabled() && !maintenanceService.canJoin(event.getConnection().getUniqueId(), cache)) {
                    event.setCancelReason(new TextComponent(localeManager.getMessage("maintenance.login_denied")));
                    event.setCancelled(true);
                }
            } catch (java.util.concurrent.TimeoutException e) {
                platform.getLogger().warning("Login check timed out for " + event.getConnection().getName() + " - blocking login for safety");
                event.setCancelReason(new TextComponent("Login verification timed out. Please try again."));
                event.setCancelled(true);
            } catch (Exception e) {
                // Unwrap ExecutionException to check for PanelUnavailableException
                Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
                if (cause instanceof PanelUnavailableException) {
                    platform.getLogger().warning("Panel 502 during login check for " + event.getConnection().getName() + " - blocking login for safety");
                    event.setCancelReason(new TextComponent("Unable to verify ban status. Login temporarily restricted for safety."));
                    event.setCancelled(true);
                } else {
                    platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
                }
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Mark player as online
        cache.setOnline(event.getPlayer().getUniqueId());

        // 2FA check for staff
        if (staff2faService != null && staff2faService.isEnabled() && PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String ip = extractIpAddress(event.getPlayer().getSocketAddress());
            staff2faService.onStaffJoin(event.getPlayer().getUniqueId(), ip);
        }

        // Staff join notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String inGameName = event.getPlayer().getName();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.join", java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
        }

        // Cache skin texture from native BungeeCord API
        String texture = platform.getPlayerSkinTexture(event.getPlayer().getUniqueId());
        if (texture != null) {
            cache.cacheSkinTexture(event.getPlayer().getUniqueId(), texture);
        }

        String ipAddress = extractIpAddress(event.getPlayer().getSocketAddress());

        // Get skin hash asynchronously (non-blocking — avoids blocking the network thread)
        WebPlayer.get(event.getPlayer().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> {
                    platform.getLogger().warning("Failed to get skin hash for " + event.getPlayer().getName() + ": " + t.getMessage());
                    return null;
                })
                .thenAccept(skinHash -> {
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
                                SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));
                                if (notification != null) {
                                    syncService.deliverLoginNotification(event.getPlayer().getUniqueId(), notification);
                                }
                            }
                        }
                    }).exceptionally(throwable -> {
                        platform.getLogger().severe("Failed to cache mute for " + event.getPlayer().getName() + ": " + throwable.getMessage());
                        return null;
                    });
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

        // Staff leave notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String inGameName = event.getPlayer().getName();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.leave", java.util.Map.of("staff", panelName, "in-game-name", inGameName)));
        }

        // Freeze logout notification
        if (freezeService.isFrozen(event.getPlayer().getUniqueId())) {
            platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification", java.util.Map.of("player", event.getPlayer().getName())));
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

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        String serverName = platform.getPlayerServer(event.getPlayer().getUniqueId());
        getHttpClient().updatePlayerServer(event.getPlayer().getUniqueId().toString(), serverName)
                .exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to update server for " + event.getPlayer().getName() + ": " + throwable.getMessage());
                    return null;
                });

        // Staff server switch notification
        if (PermissionUtil.isStaff(event.getPlayer().getUniqueId(), cache)) {
            String inGameName = event.getPlayer().getName();
            String panelName = cache.getStaffDisplayName(event.getPlayer().getUniqueId());
            if (panelName == null) panelName = inGameName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.switch", java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", serverName)));
        }
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

            // Freeze: block all commands for frozen players
            if (freezeService.isFrozen(sender.getUniqueId())) {
                event.setCancelled(true);
                sender.sendMessage(new TextComponent("§cYou cannot use commands while frozen."));
                return;
            }

            // Log command
            String cmdServerName = sender.getServer() != null ? sender.getServer().getInfo().getName() : "unknown";
            chatCommandLogService.addCommand(
                    sender.getUniqueId().toString(),
                    sender.getName(),
                    event.getMessage(),
                    cmdServerName
            );

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

        // Staff chat: if sender is in staff chat mode, redirect to staff chat
        if (staffChatService.isInStaffChat(sender.getUniqueId())) {
            event.setCancelled(true);
            String inGameName = sender.getName();
            String panelName = cache.getStaffDisplayName(sender.getUniqueId());
            String format = staffChatConfig.getFormat()
                    .replace("{player}", inGameName)
                    .replace("{panel-name}", panelName != null ? panelName : inGameName)
                    .replace("{message}", event.getMessage())
                    .replace("&", "§");
            platform.staffBroadcast(format);
            return;
        }

        // Staff chat prefix shortcut
        if (staffChatConfig.isEnabled() && event.getMessage().startsWith(staffChatConfig.getPrefix())
                && PermissionUtil.isStaff(sender.getUniqueId(), cache)) {
            event.setCancelled(true);
            String msg = event.getMessage().substring(staffChatConfig.getPrefix().length()).trim();
            if (!msg.isEmpty()) {
                String inGameName = sender.getName();
                String panelName = cache.getStaffDisplayName(sender.getUniqueId());
                String format = staffChatConfig.getFormat()
                        .replace("{player}", inGameName)
                        .replace("{panel-name}", panelName != null ? panelName : inGameName)
                        .replace("{message}", msg)
                        .replace("&", "§");
                platform.staffBroadcast(format);
            }
            return;
        }

        // Chat management: chat disabled check
        boolean isStaff = PermissionUtil.isStaff(sender.getUniqueId(), cache);
        if (!chatManagementService.canSendMessage(sender.getUniqueId(), isStaff)) {
            event.setCancelled(true);
            if (!chatManagementService.isChatEnabled()) {
                sender.sendMessage(new TextComponent(localeManager.getMessage("chat_management.chat_disabled")));
            } else {
                int remaining = chatManagementService.getSlowModeRemaining(sender.getUniqueId());
                sender.sendMessage(new TextComponent(localeManager.getMessage("chat_management.slow_mode_wait",
                        java.util.Map.of("seconds", String.valueOf(remaining)))));
            }
            return;
        }

        if (cache.isMuted(sender.getUniqueId())) {
            // Cancel the chat event
            event.setCancelled(true);
            sendMuteMessage(sender);
            return;
        }

        // Freeze: redirect frozen player chat to staff
        if (freezeService.isFrozen(sender.getUniqueId())) {
            event.setCancelled(true);
            String frozenMsg = "§c[Frozen] §f" + sender.getName() + ": §7" + event.getMessage();
            platform.staffBroadcast(frozenMsg);
            return;
        }

        // Log chat message
        chatCommandLogService.addChatMessage(
                sender.getUniqueId().toString(),
                sender.getName(),
                event.getMessage(),
                serverName
        );

        // Forward to interceptors
        for (java.util.UUID interceptor : networkChatInterceptService.getInterceptors()) {
            if (!interceptor.equals(sender.getUniqueId())) {
                platform.sendMessage(interceptor, "§8[Intercept] §f" + sender.getName() + ": §7" + event.getMessage());
            }
        }
    }

    private void sendMuteMessage(ProxiedPlayer sender) {
        Cache.CachedPlayerData data = cache.getCache().get(sender.getUniqueId());
        if (data != null) {
            String muteMessage;
            if (data.getSimpleMute() != null) {
                muteMessage = PunishmentMessages.formatMuteMessage(data.getSimpleMute(), localeManager, MessageContext.CHAT);
            } else if (data.getMute() != null) {
                muteMessage = PunishmentMessages.formatLegacyMuteMessage(data.getMute());
            } else {
                muteMessage = "§cYou are muted!";
            }
            sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(muteMessage)));
        }
    }
    
    public Cache getPunishmentCache() {
        return cache;
    }
    
    public ChatMessageCache getChatMessageCache() {
        return chatMessageCache;
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