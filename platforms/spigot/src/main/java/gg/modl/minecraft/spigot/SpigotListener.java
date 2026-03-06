package gg.modl.minecraft.spigot;

import com.google.gson.JsonObject;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
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
import gg.modl.minecraft.core.util.CommandInterceptHandler;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.LoginHandler;
import gg.modl.minecraft.core.util.WebPlayer;
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
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SpigotListener implements Listener {
    private static final long PRE_LOGIN_TIMEOUT_SECONDS = 10;

    private final SpigotPlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final boolean debugMode;
    private final List<String> mutedCommands;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final MaintenanceService maintenanceService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final ChatCommandLogService chatCommandLogService;
    private final Staff2faService staff2faService;
    private final StaffChatConfig staffChatConfig;
    private final VanishService vanishService;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String ipAddress = event.getAddress().getHostAddress();

        LoginCache.CachedLoginResult cached = loginCache.getCachedLoginResult(event.getUniqueId());
        if (cached != null) {
            platform.getLogger().debug("Using cached login result for " + event.getName());
            loginCache.storePreLoginResult(event.getUniqueId(),
                new LoginCache.PreLoginResult(cached.getResponse(), cached.getIpInfo(), cached.getSkinHash()));
            return;
        }

        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<WebPlayer> webPlayerFuture = WebPlayer.get(event.getUniqueId());

        CompletableFuture<Void> combinedFuture = ipInfoFuture
            .thenCombine(webPlayerFuture, (ipInfo, webPlayer) -> {
                String skinHash = (webPlayer != null && webPlayer.valid()) ? webPlayer.skin() : null;
                PlayerLoginRequest request = new PlayerLoginRequest(
                        event.getUniqueId().toString(), event.getName(),
                        ipAddress, skinHash, ipInfo, platform.getServerName());
                return new Object[] { request, ipInfo, skinHash };
            })
            .thenCompose(data -> {
                PlayerLoginRequest request = (PlayerLoginRequest) data[0];
                JsonObject ipInfo = (JsonObject) data[1];
                String skinHash = (String) data[2];
                return getHttpClient().playerLogin(request)
                    .thenAccept(response -> {
                        loginCache.cacheLoginResult(event.getUniqueId(), response, ipInfo, skinHash);
                        loginCache.storePreLoginResult(event.getUniqueId(),
                            new LoginCache.PreLoginResult(response, ipInfo, skinHash));
                        ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getUniqueId().toString(), ipAddress, CompletableFuture.completedFuture(ipInfo), platform.getLogger());
                    });
            })
            .exceptionally(throwable -> {
                platform.getLogger().warning("Failed to check punishments for " + event.getName() + ": " + throwable.getMessage());
                Exception error = throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable);
                loginCache.storePreLoginResult(event.getUniqueId(), new LoginCache.PreLoginResult(error));
                return null;
            });

        try {
            combinedFuture.get(PRE_LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            platform.getLogger().warning("Async pre-login timed out for " + event.getName() + ": " + e.getMessage());
            loginCache.storePreLoginResult(event.getUniqueId(), new LoginCache.PreLoginResult(e));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        LoginCache.PreLoginResult preLoginResult = loginCache.getAndRemovePreLoginResult(event.getPlayer().getUniqueId());

        if (preLoginResult == null) {
            platform.getLogger().warning("No pre-login result found for " + event.getPlayer().getName() + " - allowing login as fallback");
            return;
        }

        if (preLoginResult.hasError()) {
            LoginHandler.LoginResult errorResult = LoginHandler.handleLoginError(preLoginResult.getError());
            if (errorResult instanceof LoginHandler.LoginResult.Denied denied) {
                if (preLoginResult.getError() instanceof PanelUnavailableException) {
                    platform.getLogger().warning("Panel 502 during login check for " + event.getPlayer().getName() + " - blocking login for safety");
                }
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage(denied.message());
            } else {
                platform.getLogger().severe("Failed to check punishments for " + event.getPlayer().getName() + ": " + preLoginResult.getError().getMessage());
            }
            return;
        }

        if (!preLoginResult.isSuccess()) {
            platform.getLogger().warning("Invalid pre-login result for " + event.getPlayer().getName() + " - allowing login as fallback");
            return;
        }

        LoginHandler.LoginResult result = LoginHandler.processLoginResponse(
                preLoginResult.getResponse(), event.getPlayer().getUniqueId(),
                getHttpClient(), localeManager, syncService, maintenanceService,
                cache, debugMode, platform.getLogger());

        if (result instanceof LoginHandler.LoginResult.Denied denied) {
            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
            event.setKickMessage(denied.message());
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        ListenerHelper.handlePlayerJoin(uuid, event.getPlayer().getName(),
                platform, cache, localeManager, staff2faService, syncService);
        cacheSkinTexture(uuid);
        chatMessageCache.updatePlayerServer(platform.getServerName(), uuid.toString());

        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(uuid);
        LoginHandler.cacheLoginData(uuid,
                cachedResult != null ? cachedResult.getResponse() : null,
                cache, platform.getLogger());
    }

    private void cacheSkinTexture(java.util.UUID uuid) {
        // Try native Spigot API first (1.18.1+), fall back to WebPlayer
        String nativeTexture = platform.getPlayerSkinTexture(uuid);
        if (nativeTexture != null) {
            cache.cacheSkinTexture(uuid, nativeTexture);
            return;
        }
        WebPlayer.get(uuid)
                .thenAccept(webPlayer -> {
                    if (webPlayer != null && webPlayer.valid() && webPlayer.textureValue() != null)
                        cache.cacheSkinTexture(uuid, webPlayer.textureValue());
                })
                .exceptionally(throwable -> null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                getHttpClient(), cache, platform, localeManager, freezeService,
                staffChatService, chatManagementService, networkChatInterceptService,
                staff2faService, chatMessageCache,
                vanishService, staffModeService, bridgeService);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        var result = ChatEventHandler.handleChat(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage(),
                platform.getServerName(),
                msg -> event.getPlayer().sendMessage(msg),
                platform, cache, localeManager, chatMessageCache,
                staffChatService, staffChatConfig, chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService);
        if (result == ChatEventHandler.Result.CANCELLED) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        var result = CommandInterceptHandler.handleCommand(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getMessage(), platform.getServerName(),
                mutedCommands, cache, freezeService, chatCommandLogService);

        if (result != CommandInterceptHandler.CommandResult.ALLOWED) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(CommandInterceptHandler.getBlockMessage(
                    result, event.getPlayer().getUniqueId(), cache, localeManager));
        }
    }

}