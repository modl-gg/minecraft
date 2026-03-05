package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.*;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.UUID;
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

    /**
     * Common disconnect handler for all platforms.
     * Performs staff notifications, service cleanup, and cache removal.
     */
    public static void handlePlayerDisconnect(
            UUID uuid, String playerName,
            ModlHttpClient httpClient, Cache cache, Platform platform,
            LocaleManager localeManager, FreezeService freezeService,
            StaffChatService staffChatService, ChatManagementService chatManagementService,
            NetworkChatInterceptService networkChatInterceptService, Staff2faService staff2faService,
            ChatMessageCache chatMessageCache) {

        // Compute session duration BEFORE marking offline (which clears join time)
        long sessionDuration = cache.getSessionDuration(uuid);

        httpClient.playerDisconnect(new PlayerDisconnectRequest(uuid.toString(), sessionDuration));

        // Staff leave notification
        if (PermissionUtil.isStaff(uuid, cache)) {
            String displayName = cache.getDisplayName(uuid, playerName);
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.leave",
                    Map.of("staff", displayName, "in-game-name", playerName)));
            httpClient.reportStaffDisconnect(uuid.toString(), sessionDuration);
        }

        // Freeze logout notification
        if (freezeService.isFrozen(uuid)) {
            platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification",
                    Map.of("player", playerName)));
            freezeService.removePlayer(uuid);
        }

        // Mark player as offline
        cache.setOffline(uuid);

        // Clean up staff tools state
        staffChatService.removePlayer(uuid);
        chatManagementService.removePlayer(uuid);
        networkChatInterceptService.removePlayer(uuid);
        if (staff2faService != null) staff2faService.removePlayer(uuid);

        // Remove player from punishment cache
        cache.removePlayer(uuid);

        // Remove player from chat message cache
        chatMessageCache.removePlayer(uuid.toString());

        // Clear any pending chat input prompts
        ChatInputManager.clearOnDisconnect(uuid);
    }
}
