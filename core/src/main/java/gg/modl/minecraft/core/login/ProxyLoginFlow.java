package gg.modl.minecraft.core.login;

import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.integration.iplookup.IpEnrichmentService;
import gg.modl.minecraft.core.integration.iplookup.PendingIpLookupService;
import gg.modl.minecraft.core.integration.mojang.MojangProfiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ProxyLoginFlow {
    private final HttpClientHolder httpClientHolder;
    private final LoginCache loginCache;
    private final LoginService loginService;
    private final LoginRequestBuilder loginRequestBuilder;
    private final IpEnrichmentService ipEnrichmentService;
    private final PendingIpLookupService pendingIpLookupService;
    private final long timeoutSeconds;

    public ProxyLoginFlow(HttpClientHolder httpClientHolder, LoginCache loginCache, LoginService loginService,
                          LoginRequestBuilder loginRequestBuilder, IpEnrichmentService ipEnrichmentService,
                          PendingIpLookupService pendingIpLookupService, long timeoutSeconds) {
        this.httpClientHolder = httpClientHolder;
        this.loginCache = loginCache;
        this.loginService = loginService;
        this.loginRequestBuilder = loginRequestBuilder;
        this.ipEnrichmentService = ipEnrichmentService;
        this.pendingIpLookupService = pendingIpLookupService;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void execute(UUID uuid, String username, String ipAddress, String serverName,
                        Consumer<String> deniedSink, Runnable allowedSink) throws Exception {
        CompletableFuture<Map<String, Object>> ipInfoFuture = ipEnrichmentService.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = MojangProfiles.client().get(uuid)
                .thenApply(profile -> profile != null && profile.isValid() ? profile.getSkin() : null)
                .exceptionally(error -> null);

        PlayerLoginRequest request = loginRequestBuilder.build(
                uuid.toString(), username, ipAddress, serverName,
                ipInfoFuture, skinHashFuture, timeoutSeconds);

        PlayerLoginResponse response = httpClientHolder.getClient()
                .playerLogin(request).get(timeoutSeconds, TimeUnit.SECONDS);
        loginCache.cacheLoginResult(uuid, response, request.getIpInfo(), request.getSkinHash());
        pendingIpLookupService.handlePendingIpLookups(response, uuid.toString(), ipAddress, ipInfoFuture);

        LoginService.LoginResult result = loginService.processLoginResponse(response, uuid);
        if (result instanceof LoginService.LoginResult.Denied) {
            deniedSink.accept(((LoginService.LoginResult.Denied) result).getMessage());
        } else {
            allowedSink.run();
        }
    }
}
