package gg.modl.minecraft.spigot;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.integration.iplookup.IpEnrichmentService;
import gg.modl.minecraft.core.integration.iplookup.PendingIpLookupService;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.integration.mojang.WebPlayer;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.session.PlayerSessionService;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SpigotListener implements Listener {
    private static final long PRE_LOGIN_TIMEOUT_SECONDS = 10;

    private final SpigotPlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final ChatService chatService;
    private final CommandInterceptService commandInterceptService;
    private final LoginService loginService;
    private final PlayerSessionService playerSessionService;
    private final IpEnrichmentService ipEnrichmentService;
    private final PendingIpLookupService pendingIpLookupService;

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

        CompletableFuture<Map<String, Object>> ipInfoFuture = ipEnrichmentService.getIpInfo(ipAddress);
        CompletableFuture<WebPlayer> webPlayerFuture = MojangProfiles.client().get(event.getUniqueId());

        CompletableFuture<Void> combinedFuture = ipInfoFuture
            .thenCombine(webPlayerFuture, (ipInfo, webPlayer) -> {
                String skinHash = (webPlayer != null && webPlayer.isValid()) ? webPlayer.getSkin() : null;
                PlayerLoginRequest request = new PlayerLoginRequest(
                        event.getUniqueId().toString(), event.getName(),
                        ipAddress, skinHash, platform.getServerName(), ipInfo,
                        StartupClient.getServerInstanceId());
                return new Object[] { request, ipInfo, skinHash };
            })
            .thenCompose(data -> {
                PlayerLoginRequest request = (PlayerLoginRequest) data[0];
                @SuppressWarnings("unchecked")
                Map<String, Object> ipInfo = (Map<String, Object>) data[1];
                String skinHash = (String) data[2];
                return getHttpClient().playerLogin(request)
                    .thenAccept(response -> {
                        loginCache.cacheLoginResult(event.getUniqueId(), response, ipInfo, skinHash);
                        loginCache.storePreLoginResult(event.getUniqueId(),
                            new LoginCache.PreLoginResult(response, ipInfo, skinHash));
                        pendingIpLookupService.handlePendingIpLookups(response, event.getUniqueId().toString(), ipAddress, CompletableFuture.completedFuture(ipInfo));
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
            platform.getLogger().warning("No pre-login result found for " + event.getPlayer().getName() + " - blocking login for safety");
            denyLoginUnverified(event);
            return;
        }

        if (preLoginResult.hasError()) {
            LoginService.LoginResult errorResult = loginService.handleLoginError(preLoginResult.getError());
            if (errorResult instanceof LoginService.LoginResult.Denied) {
                LoginService.LoginResult.Denied denied = (LoginService.LoginResult.Denied) errorResult;
                if (preLoginResult.getError() instanceof PanelUnavailableException) {
                    platform.getLogger().warning("Panel 502 during login check for " + event.getPlayer().getName() + " - blocking login for safety");
                }
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage(denied.getMessage());
            } else {
                platform.getLogger().severe("Failed to verify ban status for " + event.getPlayer().getName() + ": " + preLoginResult.getError().getMessage() + " - blocking login for safety");
                denyLoginUnverified(event);
            }
            return;
        }

        if (!preLoginResult.isSuccess()) {
            platform.getLogger().warning("Invalid pre-login result for " + event.getPlayer().getName() + " - blocking login for safety");
            denyLoginUnverified(event);
            return;
        }

        LoginService.LoginResult result = loginService.processLoginResponse(
                preLoginResult.getResponse(), event.getPlayer().getUniqueId());

        if (result instanceof LoginService.LoginResult.Denied) {
            LoginService.LoginResult.Denied denied = (LoginService.LoginResult.Denied) result;
            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
            event.setKickMessage(denied.getMessage());
        }
    }

    private void denyLoginUnverified(PlayerLoginEvent event) {
        event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
        event.setKickMessage(localeManager.getMessage("api_errors.ban_check_failed"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        playerSessionService.handlePlayerJoin(uuid, event.getPlayer().getName());
        cacheSkinTexture(uuid);
        chatMessageCache.updatePlayerServer(platform.getServerName(), uuid.toString());

        LoginCache.CachedLoginResult cachedResult = loginCache.getCachedLoginResult(uuid);
        loginService.cacheLoginData(uuid, cachedResult != null ? cachedResult.getResponse() : null);
    }

    private void cacheSkinTexture(UUID uuid) {
        String nativeTexture = platform.getPlayerSkinTexture(uuid);
        if (nativeTexture != null) {
            cache.cacheSkinTexture(uuid, nativeTexture);
            return;
        }
        MojangProfiles.client().get(uuid)
                .thenAccept(webPlayer -> {
                    if (webPlayer != null && webPlayer.isValid() && webPlayer.getTextureValue() != null)
                        cache.cacheSkinTexture(uuid, webPlayer.getTextureValue());
                })
                .exceptionally(throwable -> null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSessionService.handlePlayerDisconnect(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        ChatService.Result result = chatService.handleChat(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage(),
                platform.getServerName(),
                msg -> event.getPlayer().sendMessage(msg));
        if (result == ChatService.Result.CANCELLED) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        CommandInterceptService.CommandResult result = commandInterceptService.handleCommand(
                event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                event.getMessage(), platform.getServerName());

        if (result != CommandInterceptService.CommandResult.ALLOWED) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(commandInterceptService.getBlockMessage(
                    result, event.getPlayer().getUniqueId()));
        }
    }

}
