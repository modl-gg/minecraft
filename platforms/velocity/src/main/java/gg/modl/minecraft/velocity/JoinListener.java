package gg.modl.minecraft.velocity;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
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
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.LoginHandler;
import gg.modl.minecraft.core.util.ServerSwitchHandler;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class JoinListener {
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final Logger logger;
    private final ChatMessageCache chatMessageCache;
    private final Platform platform;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final boolean debugMode;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final MaintenanceService maintenanceService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final Staff2faService staff2faService;
    private final VanishService vanishService;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getPlayer().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> null);

        JsonObject ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception ignored) {}

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getUsername(),
                ipAddress, skinHash, ipInfo, platform.getServerName()
        );

        try {
            PlayerLoginResponse response = getHttpClient().playerLogin(request).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getPlayer().getUniqueId().toString(), ipAddress, ipInfoFuture, platform.getLogger());

            LoginHandler.LoginResult result = LoginHandler.processLoginResponse(
                    response, event.getPlayer().getUniqueId(),
                    getHttpClient(), localeManager, syncService, maintenanceService,
                    cache, debugMode, platform.getLogger());

            if (result instanceof LoginHandler.LoginResult.Denied denied) {
                event.setResult(ResultedEvent.ComponentResult.denied(Colors.get(denied.message())));
            } else {
                LoginHandler.cacheLoginData(event.getPlayer().getUniqueId(), response,
                        cache, platform.getLogger());
                event.setResult(ResultedEvent.ComponentResult.allowed());
                if (debugMode) logger.info("Allowed login for {}", event.getPlayer().getUsername());
            }
        } catch (Exception e) {
            LoginHandler.LoginResult errorResult = LoginHandler.handleLoginError(e);
            if (errorResult instanceof LoginHandler.LoginResult.Denied denied) {
                logger.warn("Login blocked for {}: {}", event.getPlayer().getUsername(), denied.message());
                event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text(denied.message()).color(NamedTextColor.RED)));
            } else {
                logger.error("Failed to check punishments for {} - allowing login as fallback", event.getPlayer().getUsername(), e);
                event.setResult(ResultedEvent.ComponentResult.allowed());
            }
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        ListenerHelper.handlePlayerJoin(uuid, event.getPlayer().getUsername(),
                platform, cache, localeManager, staff2faService, syncService);

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                getHttpClient(), cache, platform, localeManager, freezeService,
                staffChatService, chatManagementService, networkChatInterceptService,
                staff2faService, chatMessageCache,
                vanishService, staffModeService, bridgeService);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ServerSwitchHandler.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                event.getServer().getServerInfo().getName(),
                getHttpClient(), cache, localeManager, platform);
    }

}
