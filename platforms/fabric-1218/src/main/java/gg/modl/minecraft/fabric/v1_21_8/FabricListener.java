package gg.modl.minecraft.fabric.v1_21_8;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.chat.ChatService;
import gg.modl.minecraft.core.integration.iplookup.IpEnrichmentService;
import gg.modl.minecraft.core.integration.iplookup.PendingIpLookupService;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.integration.mojang.WebPlayer;
import gg.modl.minecraft.core.login.LoginExecutor;
import gg.modl.minecraft.core.login.LoginService;
import gg.modl.minecraft.core.session.PlayerSessionService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import java.util.concurrent.TimeUnit;
import net.minecraft.text.Text;

@RequiredArgsConstructor
public class FabricListener {
    private static final long LOGIN_TIMEOUT_SECONDS = 10;
    private static final int LOGIN_EXECUTOR_THREADS = 4;
    private static final int LOGIN_QUEUE_CAPACITY = 64;

    private final FabricPlatform platform;
    private final Cache cache;
    private final HttpClientHolder httpClientHolder;
    private final ChatMessageCache chatMessageCache;
    private final LoginCache loginCache;
    private final ChatService chatService;
    private final LoginService loginService;
    private final PlayerSessionService playerSessionService;
    private final IpEnrichmentService ipEnrichmentService;
    private final PendingIpLookupService pendingIpLookupService;
    private final MinecraftServer server;
    private final LoginExecutor loginExecutor = new LoginExecutor(
            "modl-fabric-login", LOGIN_EXECUTOR_THREADS, LOGIN_QUEUE_CAPACITY);
    private final Set<UUID> pendingVerdicts = ConcurrentHashMap.newKeySet();

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

        pendingVerdicts.add(uuid);

        LoginCache.CachedLoginResult cached = loginCache.getCachedLoginResult(uuid);
        if (cached != null) {
            platform.getLogger().debug("Using cached login result for " + playerName);
            handleLoginSuccess(uuid, playerName, ipAddress, cached.getResponse(), cached.getIpInfo());
            return;
        }

        try {
            loginExecutor.runAsync(() -> {
                CompletableFuture<Map<String, Object>> ipInfoFuture = ipEnrichmentService.getIpInfo(ipAddress);
                CompletableFuture<WebPlayer> webPlayerFuture = MojangProfiles.client().get(uuid);

                ipInfoFuture.thenCombine(webPlayerFuture, (ipInfo, webPlayer) -> {
                    String skinHash = (webPlayer != null && webPlayer.isValid()) ? webPlayer.getSkin() : null;
                    PlayerLoginRequest request = new PlayerLoginRequest(
                            uuid.toString(), playerName,
                            ipAddress, skinHash, platform.getServerName(), ipInfo,
                            StartupClient.getServerInstanceId());
                    return new Object[]{request, ipInfo, skinHash};
                }).thenCompose(data -> {
                    PlayerLoginRequest request = (PlayerLoginRequest) data[0];
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ipInfo = (Map<String, Object>) data[1];
                    String skinHash = (String) data[2];
                    return getHttpClient().playerLogin(request)
                            .orTimeout(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .thenAccept(response -> {
                                loginCache.cacheLoginResult(uuid, response, ipInfo, skinHash);
                                pendingIpLookupService.handlePendingIpLookups(
                                        response, uuid.toString(),
                                        ipAddress, CompletableFuture.completedFuture(ipInfo));
                                handleLoginSuccess(uuid, playerName, ipAddress, response, ipInfo);
                            });
                }).exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to check punishments for " + playerName + ": " + throwable.getMessage());
                    Exception error = throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable);
                    LoginService.LoginResult errorResult = loginService.handleLoginError(error);
                    if (errorResult instanceof LoginService.LoginResult.Denied denied) {
                        kickForLoginFailure(uuid, denied.getMessage());
                    } else {
                        kickForLoginFailure(uuid, "Unable to verify ban status. Login temporarily restricted for safety.");
                    }
                    return null;
                });
            });
        } catch (RejectedExecutionException e) {
            platform.getLogger().warning("Login executor rejected " + playerName + " - blocking login for safety");
            kickForLoginFailure(uuid, "Login verification is temporarily unavailable. Please try again.");
        }
    }

    private void kickForLoginFailure(UUID uuid, String reason) {
        pendingVerdicts.remove(uuid);
        server.execute(() -> platform.kickPlayer(platform.getAbstractPlayer(uuid, false), reason));
    }

    public void shutdown() {
        loginExecutor.shutdown();
    }

    private void handleLoginSuccess(UUID uuid, String playerName, String ipAddress,
                                    PlayerLoginResponse response,
                                    Map<String, Object> ipInfo) {
        LoginService.LoginResult result = loginService.processLoginResponse(response, uuid);

        if (result instanceof LoginService.LoginResult.Denied denied) {
            kickForLoginFailure(uuid, denied.getMessage());
            return;
        }

        completeJoin(uuid, playerName, response);
    }

    private void completeJoin(UUID uuid, String playerName,
                              PlayerLoginResponse response) {
        pendingVerdicts.remove(uuid);
        server.execute(() -> {
            if (!platform.isOnline(uuid)) return;
            playerSessionService.handlePlayerJoin(uuid, playerName);
            cacheSkinTexture(uuid);
            chatMessageCache.updatePlayerServer(platform.getServerName(), uuid.toString());
            loginService.cacheLoginData(uuid, response);
        });
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
                .exceptionally(throwable -> {
                    platform.getLogger().debug("Failed to cache skin texture for " + uuid + ": " + throwable.getMessage());
                    return null;
                });
    }

    private void onPlayerDisconnect(ServerPlayerEntity player) {
        pendingVerdicts.remove(player.getUuid());
        playerSessionService.handlePlayerDisconnect(
                player.getUuid(), player.getName().getString());
    }

    private boolean onChatMessage(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters params) {
        if (pendingVerdicts.contains(player.getUuid())) {
            return false;
        }
        ChatService.Result result = chatService.handleChat(
                player.getUuid(), player.getName().getString(), message.getContent().getString(),
                platform.getServerName(),
                msg -> player.sendMessage(Text.literal(msg), false));
        return result != ChatService.Result.CANCELLED;
    }
}
