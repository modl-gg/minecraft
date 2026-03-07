package gg.modl.minecraft.velocity;

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
import gg.modl.minecraft.core.impl.cache.PlayerProfileRegistry;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.LoginHandler;
import gg.modl.minecraft.core.util.ServerSwitchHandler;
import gg.modl.minecraft.core.util.WebPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JoinListener {
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final Logger logger;
    private final ChatMessageCache chatMessageCache;
    private final Platform platform;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final MaintenanceService maintenanceService;
    private final Staff2faService staff2faService;
    private final BridgeService bridgeService;
    private final PlayerProfileRegistry registry;
    private final boolean debugMode;

    /** Buffers login responses between onLogin and onPostLogin so profile exists before caching. */
    private final Map<UUID, PlayerLoginResponse> pendingLoginData = new ConcurrentHashMap<>();

    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    public JoinListener(HttpClientHolder httpClientHolder, Cache cache, Logger logger,
                        ChatMessageCache chatMessageCache, Platform platform, SyncService syncService,
                        LocaleManager localeManager, MaintenanceService maintenanceService,
                        Staff2faService staff2faService, BridgeService bridgeService,
                        PlayerProfileRegistry registry, boolean debugMode) {
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.logger = logger;
        this.chatMessageCache = chatMessageCache;
        this.platform = platform;
        this.syncService = syncService;
        this.localeManager = localeManager;
        this.maintenanceService = maintenanceService;
        this.staff2faService = staff2faService;
        this.bridgeService = bridgeService;
        this.registry = registry;
        this.debugMode = debugMode;
    }

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        CompletableFuture<Map<String, Object>> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getPlayer().getUniqueId())
                .thenApply(wp -> wp != null && wp.isValid() ? wp.getSkin() : null)
                .exceptionally(t -> null);

        Map<String, Object> ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception ignored) {}

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getUsername(),
                ipAddress, skinHash, platform.getServerName(), ipInfo
        );

        try {
            PlayerLoginResponse response = getHttpClient().playerLogin(request).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getPlayer().getUniqueId().toString(), ipAddress, ipInfoFuture, platform.getLogger());

            LoginHandler.LoginResult result = LoginHandler.processLoginResponse(
                    response, event.getPlayer().getUniqueId(),
                    getHttpClient(), localeManager, syncService, maintenanceService,
                    cache, debugMode, platform.getLogger());

            if (result instanceof LoginHandler.LoginResult.Denied denied) {
                event.setResult(ResultedEvent.ComponentResult.denied(Colors.get(denied.getMessage())));
            } else {
                pendingLoginData.put(event.getPlayer().getUniqueId(), response);
                event.setResult(ResultedEvent.ComponentResult.allowed());
                if (debugMode) logger.info("Allowed login for {}", event.getPlayer().getUsername());
            }
        } catch (Exception e) {
            LoginHandler.LoginResult errorResult = LoginHandler.handleLoginError(e);
            if (errorResult instanceof LoginHandler.LoginResult.Denied denied) {
                logger.warn("Login blocked for {}: {}", event.getPlayer().getUsername(), denied.getMessage());
                event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text(denied.getMessage()).color(NamedTextColor.RED)));
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

        PlayerLoginResponse response = pendingLoginData.remove(uuid);
        LoginHandler.cacheLoginData(uuid, response, cache, platform.getLogger());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingLoginData.remove(event.getPlayer().getUniqueId());
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                getHttpClient(), cache, platform, localeManager,
                chatMessageCache, bridgeService, registry);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ServerSwitchHandler.handleServerSwitch(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                event.getServer().getServerInfo().getName(),
                getHttpClient(), cache, localeManager, platform);
    }

}
