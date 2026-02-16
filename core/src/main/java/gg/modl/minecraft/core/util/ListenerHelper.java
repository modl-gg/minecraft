package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ListenerHelper {

    private ListenerHelper() {}

    /**
     * Convert a map from the login response to a PlayerNotification object.
     */
    public static SyncResponse.PlayerNotification mapToPlayerNotification(Map<String, Object> data, Logger logger) {
        try {
            SyncResponse.PlayerNotification notification = new SyncResponse.PlayerNotification();
            notification.setId((String) data.get("id"));
            notification.setMessage((String) data.get("message"));
            notification.setType((String) data.get("type"));

            if (data.get("timestamp") instanceof Number) {
                notification.setTimestamp(((Number) data.get("timestamp")).longValue());
            }

            notification.setTargetPlayerUuid((String) data.get("targetPlayerUuid"));

            // Handle nested data map
            Object nestedData = data.get("data");
            if (nestedData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) nestedData;
                notification.setData(dataMap);
            }

            return notification;
        } catch (Exception e) {
            logger.warning("Failed to convert notification data: " + e.getMessage());
            return null;
        }
    }

    /**
     * Acknowledge that a ban was successfully enforced (player denied login).
     */
    public static void acknowledgeBanEnforcement(ModlHttpClient httpClient, SimplePunishment ban, String playerUuid, boolean debugMode, Logger logger) {
        try {
            PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                    ban.getId(),
                    playerUuid,
                    java.time.Instant.now().toString(),
                    true, // success
                    null // no error message
            );

            httpClient.acknowledgePunishment(request).thenAccept(response -> {
                if (debugMode) {
                    logger.info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
                }
            }).exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to acknowledge ban enforcement for punishment " + ban.getId() + ": " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error acknowledging ban enforcement for punishment " + ban.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Handle pending IP lookups requested by the backend.
     * Reuses the original ipInfoFuture for the player's current IP to avoid redundant API calls.
     */
    public static void handlePendingIpLookups(ModlHttpClient httpClient, PlayerLoginResponse response, String minecraftUUID, String originalIp, CompletableFuture<JsonObject> originalIpInfoFuture, Logger logger) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) {
            return;
        }
        for (String ip : response.getPendingIpLookups()) {
            CompletableFuture<JsonObject> ipInfoFuture = ip.equals(originalIp) && originalIpInfoFuture != null
                    ? originalIpInfoFuture
                    : IpApiClient.getIpInfo(ip);
            ipInfoFuture.thenAccept(ipInfo -> {
                if (ipInfo != null && ipInfo.has("status") && "success".equals(ipInfo.get("status").getAsString())) {
                    httpClient.submitIpInfo(
                            minecraftUUID,
                            ip,
                            ipInfo.has("countryCode") ? ipInfo.get("countryCode").getAsString() : null,
                            ipInfo.has("regionName") ? ipInfo.get("regionName").getAsString() : null,
                            ipInfo.has("as") ? ipInfo.get("as").getAsString() : null,
                            ipInfo.has("proxy") && ipInfo.get("proxy").getAsBoolean(),
                            ipInfo.has("hosting") && ipInfo.get("hosting").getAsBoolean()
                    ).exceptionally(throwable -> {
                        logger.warning("Failed to submit IP info for " + ip + ": " + throwable.getMessage());
                        return null;
                    });
                }
            }).exceptionally(throwable -> {
                logger.warning("Failed to lookup IP " + ip + ": " + throwable.getMessage());
                return null;
            });
        }
    }
}
