package gg.modl.minecraft.core.integration.iplookup;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class PendingIpLookupService {
    private final HttpClientHolder httpClientHolder;
    private final IpEnrichmentService ipEnrichmentService;
    private final PluginLogger logger;

    public PendingIpLookupService(HttpClientHolder httpClientHolder, IpEnrichmentService ipEnrichmentService, PluginLogger logger) {
        this.httpClientHolder = httpClientHolder;
        this.ipEnrichmentService = ipEnrichmentService;
        this.logger = logger;
    }

    public void handlePendingIpLookups(PlayerLoginResponse response, String minecraftUUID, String originalIp,
                                       CompletableFuture<Map<String, Object>> originalIpInfoFuture) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) return;

        for (String ip : response.getPendingIpLookups()) {
            CompletableFuture<Map<String, Object>> ipInfoFuture = ip.equals(originalIp) && originalIpInfoFuture != null
                    ? originalIpInfoFuture
                    : ipEnrichmentService.getIpInfo(ip);
            ipInfoFuture.thenAccept(ipInfo -> submitIpInfoIfSuccess(minecraftUUID, ip, ipInfo))
                    .exceptionally(throwable -> {
                        logger.warning("Failed to lookup IP " + ip + ": " + throwable.getMessage());
                        return null;
                    });
        }
    }

    private void submitIpInfoIfSuccess(String minecraftUUID, String ip, Map<String, Object> ipInfo) {
        if (ipInfo == null || !"success".equals(ipInfo.get("status"))) return;

        ModlHttpClient httpClient = httpClientHolder.getClient();
        httpClient.submitIpInfo(
                minecraftUUID,
                ip,
                (String) ipInfo.get("countryCode"),
                (String) ipInfo.get("regionName"),
                (String) ipInfo.get("as"),
                Boolean.TRUE.equals(ipInfo.get("proxy")),
                Boolean.TRUE.equals(ipInfo.get("hosting"))
        ).exceptionally(throwable -> {
            logger.warning("Failed to submit IP info for " + ip + ": " + throwable.getMessage());
            return null;
        });
    }
}
