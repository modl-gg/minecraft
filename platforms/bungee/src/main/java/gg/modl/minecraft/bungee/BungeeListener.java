package gg.modl.minecraft.bungee;

import com.google.gson.JsonObject;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.config.StaffChatConfig;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.cache.LoginCache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.ChatEventHandler;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.MutedCommandUtil;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.StringUtil;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
public class BungeeListener implements Listener {

    private final BungeePlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final boolean debugMode;
    private final List<String> mutedCommands;
    private final Plugin plugin;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final MaintenanceService maintenanceService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final ChatCommandLogService chatCommandLogService;
    private final Staff2faService staff2faService;
    private final StaffChatConfig staffChatConfig;
    private final LoginCache loginCache;
    private final VanishService vanishService;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
        // BungeeCord async intent: waits for completion without blocking Netty thread
        event.registerIntent(plugin);

        CompletableFuture.runAsync(() -> {
            try {
                performLoginCheck(event);
            } catch (java.util.concurrent.TimeoutException e) {
                platform.getLogger().warning("Login check timed out for " + event.getConnection().getName() + " - blocking login for safety");
                denyLogin(event, "Login verification timed out. Please try again.");
            } catch (Exception e) {
                handleLoginException(event, e);
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    private void performLoginCheck(LoginEvent event) throws Exception {
        String ipAddress = extractIpAddress(event.getConnection().getSocketAddress());

        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getConnection().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> null);

        // getNow() avoids blocking -- backend will request IP lookup via pendingIpLookups if null
        JsonObject ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception ignored) {}

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getConnection().getUniqueId().toString(),
                event.getConnection().getName(),
                ipAddress, skinHash, ipInfo, platform.getServerName()
        );

        PlayerLoginResponse response = getHttpClient().playerLogin(request).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        loginCache.cacheLoginResult(event.getConnection().getUniqueId(), response, ipInfo, skinHash);
        ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getConnection().getUniqueId().toString(), ipAddress, ipInfoFuture, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));

        if (response.hasActiveBan()) {
            SimplePunishment ban = response.getActiveBan();
            denyLogin(event, PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN));
            if (!ban.isStarted()) {
                ListenerHelper.acknowledgeBanEnforcement(getHttpClient(), ban, event.getConnection().getUniqueId().toString(), debugMode, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));
            }
        } else if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
            denyLogin(event, localeManager.getMessage("stat_wipe.kick_message"));
            for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) syncService.executeStatWipeFromLogin(statWipe);
        }

        if (!event.isCancelled() && maintenanceService.isEnabled() && !maintenanceService.canJoin(event.getConnection().getUniqueId(), cache)) {
            denyLogin(event, localeManager.getMessage("maintenance.login_denied"));
        }
    }

    private void handleLoginException(LoginEvent event, Exception e) {
        Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof PanelUnavailableException) {
            platform.getLogger().warning("Panel 502 during login check for " + event.getConnection().getName() + " - blocking login for safety");
            denyLogin(event, "Unable to verify ban status. Login temporarily restricted for safety.");
        } else {
            platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
        }
    }

    private void denyLogin(LoginEvent event, String reason) {
        event.setCancelReason(new TextComponent(reason));
        event.setCancelled(true);
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        ListenerHelper.handlePlayerJoin(uuid, event.getPlayer().getName(),
                platform, cache, localeManager, staff2faService, syncService);

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);

        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(uuid);
        ListenerHelper.processLoginResponse(uuid,
                cachedResult != null ? cachedResult.getResponse() : null,
                cache, syncService, java.util.logging.Logger.getLogger(BungeeListener.class.getName()));
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                getHttpClient(), cache, platform, localeManager, freezeService,
                staffChatService, chatManagementService, networkChatInterceptService,
                staff2faService, chatMessageCache,
                vanishService, staffModeService, bridgeService);
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        String serverName = platform.getPlayerServer(uuid);
        getHttpClient().updatePlayerServer(uuid.toString(), serverName)
                .exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to update server for " + event.getPlayer().getName() + ": " + throwable.getMessage());
                    return null;
                });

        if (!PermissionUtil.isStaff(uuid, cache)) return;
        String inGameName = event.getPlayer().getName();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.switch",
                java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", serverName)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (event.getSender() == null) return;

        if (event.isCommand()) {
            handleCommand(event);
            return;
        }

        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();
        String serverName = getPlayerServerName(sender);

        var result = ChatEventHandler.handleChat(
                sender.getUniqueId(), sender.getName(), event.getMessage(), serverName,
                msg -> sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(msg))),
                platform, cache, localeManager, chatMessageCache,
                staffChatService, staffChatConfig, chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService);
        if (result == ChatEventHandler.Result.CANCELLED) event.setCancelled(true);
    }

    private void handleCommand(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer sender)) return;

        if (freezeService.isFrozen(sender.getUniqueId())) {
            event.setCancelled(true);
            sender.sendMessage(new TextComponent(localeManager.getMessage("freeze.command_blocked")));
            return;
        }

        chatCommandLogService.addCommand(
                sender.getUniqueId().toString(), sender.getName(),
                event.getMessage(), getPlayerServerName(sender));

        if (cache.isMuted(sender.getUniqueId()) && MutedCommandUtil.isBlockedCommand(event.getMessage(), mutedCommands)) {
            event.setCancelled(true);
            sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(
                    PunishmentMessages.getMuteMessage(sender.getUniqueId(), cache, localeManager))));
        }
    }

    private String getPlayerServerName(ProxiedPlayer player) {
        return player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
    }

    private String extractIpAddress(java.net.SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
        // Fallback: parse from string representation
        String addr = socketAddress.toString();
        if (addr.startsWith("/")) addr = addr.substring(1);
        if (addr.contains(":")) addr = addr.substring(0, addr.indexOf(":"));
        return addr;
    }
}