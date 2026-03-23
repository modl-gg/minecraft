package gg.modl.minecraft.bungee;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
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
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.util.ChatEventHandler;
import gg.modl.minecraft.core.util.CommandInterceptHandler;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.LoginHandler;
import gg.modl.minecraft.core.util.ServerSwitchHandler;
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
import java.util.Map;
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
    private final BridgeService bridgeService;
    private final CachedProfileRegistry registry;
    private final boolean debugMode;

    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(LoginEvent event) {
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

        CompletableFuture<Map<String, Object>> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getConnection().getUniqueId())
                .thenApply(wp -> wp != null && wp.isValid() ? wp.getSkin() : null)
                .exceptionally(t -> null);

        Map<String, Object> ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception ignored) {}

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getConnection().getUniqueId().toString(),
                event.getConnection().getName(),
                ipAddress, skinHash, platform.getServerName(), ipInfo
        );

        PlayerLoginResponse response = getHttpClient().playerLogin(request).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        loginCache.cacheLoginResult(event.getConnection().getUniqueId(), response, ipInfo, skinHash);
        ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getConnection().getUniqueId().toString(), ipAddress, ipInfoFuture, platform.getLogger());

        LoginHandler.LoginResult result = LoginHandler.processLoginResponse(
                response, event.getConnection().getUniqueId(),
                getHttpClient(), localeManager, syncService, maintenanceService,
                cache, debugMode, platform.getLogger());

        if (result instanceof LoginHandler.LoginResult.Denied) {
            LoginHandler.LoginResult.Denied denied = (LoginHandler.LoginResult.Denied) result;
            denyLogin(event, denied.getMessage());
        }
    }

    private void handleLoginException(LoginEvent event, Exception e) {
        LoginHandler.LoginResult result = LoginHandler.handleLoginError(e);
        if (result instanceof LoginHandler.LoginResult.Denied) {
            LoginHandler.LoginResult.Denied denied = (LoginHandler.LoginResult.Denied) result;
            platform.getLogger().warning("Login blocked for " + event.getConnection().getName() + ": " + denied.getMessage());
            denyLogin(event, denied.getMessage());
        } else platform.getLogger().severe("Failed to check punishments for " + event.getConnection().getName() + ": " + e.getMessage());
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
        LoginHandler.cacheLoginData(uuid,
                cachedResult != null ? cachedResult.getResponse() : null,
                cache, platform.getLogger());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                getHttpClient(), cache, platform, localeManager,
                chatMessageCache, bridgeService, registry);
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ServerSwitchHandler.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                platform.getPlayerServer(event.getPlayer().getUniqueId()),
                getHttpClient(), cache, localeManager, platform);
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

        ChatEventHandler.Result result = ChatEventHandler.handleChat(
                sender.getUniqueId(), sender.getName(), event.getMessage(), serverName,
                msg -> sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(msg))),
                platform, cache, localeManager, chatMessageCache,
                staffChatService, staffChatConfig, chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService);
        if (result == ChatEventHandler.Result.CANCELLED) event.setCancelled(true);
    }

    private void handleCommand(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer)) return;
        ProxiedPlayer sender = (ProxiedPlayer) event.getSender();

        CommandInterceptHandler.CommandResult result = CommandInterceptHandler.handleCommand(
                sender.getUniqueId(), sender.getName(),
                event.getMessage(), getPlayerServerName(sender),
                mutedCommands, cache, freezeService, chatCommandLogService);

        if (result != CommandInterceptHandler.CommandResult.ALLOWED) {
            event.setCancelled(true);
            String message = CommandInterceptHandler.getBlockMessage(
                    result, sender.getUniqueId(), cache, localeManager);
            sender.sendMessage(new TextComponent(StringUtil.unescapeNewlines(message)));
        }
    }

    private String getPlayerServerName(ProxiedPlayer player) {
        return player.getServer() != null ? player.getServer().getInfo().getName() : "unknown";
    }

    private String extractIpAddress(java.net.SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
        String addr = socketAddress.toString();
        if (addr.startsWith("/")) addr = addr.substring(1);
        if (addr.contains(":")) addr = addr.substring(0, addr.indexOf(":"));
        return addr;
    }
}
