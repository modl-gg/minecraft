package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.integration.iplookup.IpEnrichmentService;
import gg.modl.minecraft.core.integration.iplookup.PendingIpLookupService;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;
import gg.modl.minecraft.core.util.Java8Collections;
import lombok.Value;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class ProxyLoginFlow {
    private final HttpClientHolder httpClientHolder;
    private final LoginCache loginCache;
    private final LoginService loginService;
    private final IpEnrichmentService ipEnrichmentService;
    private final PendingIpLookupService pendingIpLookupService;
    private final long timeoutSeconds;

    public ProxyLoginFlow(HttpClientHolder httpClientHolder, LoginCache loginCache, LoginService loginService,
                          IpEnrichmentService ipEnrichmentService, PendingIpLookupService pendingIpLookupService,
                          long timeoutSeconds) {
        this.httpClientHolder = httpClientHolder;
        this.loginCache = loginCache;
        this.loginService = loginService;
        this.ipEnrichmentService = ipEnrichmentService;
        this.pendingIpLookupService = pendingIpLookupService;
        this.timeoutSeconds = timeoutSeconds;
    }

    public CompletableFuture<LoginService.LoginResult> begin(UUID uuid, String username, String ipAddress,
                                                             String serverName) {
        try {
            return attempt(uuid, username, ipAddress, serverName);
        } catch (Throwable failure) {
            return CompletableFuture.completedFuture(loginService.handleLoginError(asException(failure)));
        }
    }

    private CompletableFuture<LoginService.LoginResult> attempt(UUID uuid, String username, String ipAddress,
                                                                String serverName) {
        CompletableFuture<Map<String, Object>> ipInfoFuture = ipEnrichmentService.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = MojangProfiles.client().get(uuid)
                .thenApply(profile -> profile != null && profile.isValid() ? profile.getSkin() : null);

        long enrichmentTimeoutSeconds = Math.max(1, timeoutSeconds * 2 / 5);
        CompletableFuture<LoginExchange> exchange = bestEffort(ipInfoFuture, enrichmentTimeoutSeconds)
                .thenCombine(bestEffort(skinHashFuture, enrichmentTimeoutSeconds), (ipInfo, skinHash) -> new PlayerLoginRequest(
                        uuid.toString(), username, ipAddress, skinHash, serverName, ipInfo,
                        StartupClient.getServerInstanceId()))
                .thenComposeAsync(request -> httpClientHolder.getClient().playerLogin(request)
                        .thenApply(response -> new LoginExchange(request, response)));

        return Java8Collections.orTimeout(exchange, timeoutSeconds, TimeUnit.SECONDS)
                .handleAsync((completed, failure) -> failure == null
                        ? settle(completed, uuid, ipAddress, ipInfoFuture)
                        : loginService.handleLoginError(asException(failure)));
    }

    private LoginService.LoginResult settle(LoginExchange exchange, UUID uuid, String ipAddress,
                                            CompletableFuture<Map<String, Object>> ipInfoFuture) {
        try {
            loginCache.cacheLoginResult(uuid, exchange.getResponse(), exchange.getRequest().getIpInfo(),
                    exchange.getRequest().getSkinHash());
            pendingIpLookupService.handlePendingIpLookups(exchange.getResponse(), uuid.toString(), ipAddress,
                    ipInfoFuture);
            return loginService.processLoginResponse(exchange.getResponse(), uuid);
        } catch (Throwable failure) {
            return loginService.handleLoginError(asException(failure));
        }
    }

    private static <T> CompletableFuture<T> bestEffort(CompletableFuture<T> lookup, long timeoutSeconds) {
        CompletableFuture<T> detached = lookup.thenApply(value -> value);
        return Java8Collections.orTimeout(detached, timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(failure -> null);
    }

    private static Exception asException(Throwable failure) {
        Throwable cause = (failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null ? failure.getCause() : failure;
        return cause instanceof Exception ? (Exception) cause : new ExecutionException(cause);
    }

    @Value
    private static class LoginExchange {
        PlayerLoginRequest request;
        PlayerLoginResponse response;
    }
}
