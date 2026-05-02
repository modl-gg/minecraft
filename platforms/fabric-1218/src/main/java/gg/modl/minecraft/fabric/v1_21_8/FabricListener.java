package gg.modl.minecraft.fabric.v1_21_8;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.config.ConfigManager.StaffChatConfig;
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
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.LoginHandler;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import java.util.concurrent.TimeUnit;
import net.minecraft.text.Text;

@RequiredArgsConstructor
public class FabricListener {
    private static final long LOGIN_TIMEOUT_SECONDS = 10;

    private final FabricPlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final LoginCache loginCache;
    private final List<String> mutedCommands;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final MaintenanceService maintenanceService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final ChatCommandLogService chatCommandLogService;
    private final Staff2faService staff2faService;
    private final StaffChatConfig staffChatConfig;
    private final BridgeService bridgeService;
    private final CachedProfileRegistry registry;
    private final boolean debugMode;
    private final MinecraftServer server;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> onPlayerDisconnect(handler.getPlayer()));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::onChatMessage);
    }

    private void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String playerName = player.getName().getString();
        String ipAddress = player.getIp();

        CompletableFuture.runAsync(() -> {
            try {
                LoginCache.CachedLoginResult cached = loginCache.getCachedLoginResult(uuid);
                if (cached != null) {
                    platform.getLogger().debug("Using cached login result for " + playerName);
                    handleLoginSuccess(uuid, playerName, ipAddress, cached.getResponse(), cached.getIpInfo());
                    return;
                }

                CompletableFuture<Map<String, Object>> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
                CompletableFuture<WebPlayer> webPlayerFuture = WebPlayer.get(uuid);

                ipInfoFuture.thenCombine(webPlayerFuture, (ipInfo, webPlayer) -> {
                    String skinHash = (webPlayer != null && webPlayer.isValid()) ? webPlayer.getSkin() : null;
                    PlayerLoginRequest request = new PlayerLoginRequest(
                            uuid.toString(), playerName,
                            ipAddress, skinHash, platform.getServerName(), ipInfo);
                    request.setServerInstanceId(StartupClient.getServerInstanceId());
                    return new Object[]{request, ipInfo, skinHash};
                }).thenCompose(data -> {
                    PlayerLoginRequest request = (PlayerLoginRequest) data[0];
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ipInfo = (Map<String, Object>) data[1];
                    String skinHash = (String) data[2];
                    return getHttpClient().playerLogin(request)
                            .thenAccept(response -> {
                                loginCache.cacheLoginResult(uuid, response, ipInfo, skinHash);
                                ListenerHelper.handlePendingIpLookups(
                                        getHttpClient(), response, uuid.toString(),
                                        ipAddress, CompletableFuture.completedFuture(ipInfo),
                                        platform.getLogger());
                                handleLoginSuccess(uuid, playerName, ipAddress, response, ipInfo);
                            });
                }).exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to check punishments for " + playerName + ": " + throwable.getMessage());
                    Exception error = throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable);
                    LoginHandler.LoginResult errorResult = LoginHandler.handleLoginError(error);
                    if (errorResult instanceof LoginHandler.LoginResult.Denied) {
                        LoginHandler.LoginResult.Denied denied = (LoginHandler.LoginResult.Denied) errorResult;
                        server.execute(() -> platform.kickPlayer(
                                platform.getAbstractPlayer(uuid, false), denied.getMessage()));
                    } else {
                        completeJoin(uuid, playerName, null);
                    }
                    return null;
                }).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                platform.getLogger().warning("Async login timed out for " + playerName + ": " + e.getMessage());
                completeJoin(uuid, playerName, null);
            }
        });
    }

    private void handleLoginSuccess(UUID uuid, String playerName, String ipAddress,
                                    PlayerLoginResponse response,
                                    Map<String, Object> ipInfo) {
        LoginHandler.LoginResult result = LoginHandler.processLoginResponse(
                response, uuid, getHttpClient(), localeManager, syncService,
                maintenanceService, cache, debugMode, platform.getLogger());

        if (result instanceof LoginHandler.LoginResult.Denied) {
            LoginHandler.LoginResult.Denied denied = (LoginHandler.LoginResult.Denied) result;
            server.execute(() -> platform.kickPlayer(
                    platform.getAbstractPlayer(uuid, false), denied.getMessage()));
            return;
        }

        completeJoin(uuid, playerName, response);
    }

    private void completeJoin(UUID uuid, String playerName,
                              PlayerLoginResponse response) {
        server.execute(() -> {
            ListenerHelper.handlePlayerJoin(uuid, playerName,
                    platform, cache, localeManager, staff2faService, syncService);
            cacheSkinTexture(uuid);
            chatMessageCache.updatePlayerServer(platform.getServerName(), uuid.toString());
            LoginHandler.cacheLoginData(uuid, response, cache, platform.getLogger());
        });
    }

    private void cacheSkinTexture(UUID uuid) {
        String nativeTexture = platform.getPlayerSkinTexture(uuid);
        if (nativeTexture != null) {
            cache.cacheSkinTexture(uuid, nativeTexture);
            return;
        }
        WebPlayer.get(uuid)
                .thenAccept(webPlayer -> {
                    if (webPlayer != null && webPlayer.isValid() && webPlayer.getTextureValue() != null)
                        cache.cacheSkinTexture(uuid, webPlayer.getTextureValue());
                })
                .exceptionally(throwable -> null);
    }

    private void onPlayerDisconnect(ServerPlayerEntity player) {
        ListenerHelper.handlePlayerDisconnect(
                player.getUuid(), player.getName().getString(),
                getHttpClient(), cache, platform, localeManager,
                chatMessageCache, bridgeService, registry);
    }

    private boolean onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters params) {
        ChatEventHandler.Result result = ChatEventHandler.handleChat(
                player.getUuid(), player.getName().getString(), message.getContent().getString(),
                platform.getServerName(),
                msg -> player.sendMessage(Text.literal(msg), false),
                platform, cache, localeManager, chatMessageCache,
                staffChatService, staffChatConfig, chatManagementService,
                freezeService, chatCommandLogService, networkChatInterceptService);
        return result != ChatEventHandler.Result.CANCELLED;
    }
}
