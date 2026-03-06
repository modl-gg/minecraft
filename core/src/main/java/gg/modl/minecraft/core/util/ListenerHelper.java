package gg.modl.minecraft.core.util;

import com.google.gson.JsonObject;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.impl.commands.player.IAmMutedCommand;
import gg.modl.minecraft.core.impl.menus.util.ChatInputManager;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.sync.SyncService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ListenerHelper {

    private ListenerHelper() {}

    public static SyncResponse.PlayerNotification mapToPlayerNotification(Map<String, Object> data, PluginLogger logger) {
        try {
            SyncResponse.PlayerNotification notification = new SyncResponse.PlayerNotification();
            notification.setId((String) data.get("id"));
            notification.setMessage((String) data.get("message"));
            notification.setType((String) data.get("type"));
            if (data.get("timestamp") instanceof Number) notification.setTimestamp(((Number) data.get("timestamp")).longValue());
            notification.setTargetPlayerUuid((String) data.get("targetPlayerUuid"));

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

    public static void acknowledgeBanEnforcement(ModlHttpClient httpClient, SimplePunishment ban, String playerUuid, boolean debugMode, PluginLogger logger) {
        try {
            PunishmentAcknowledgeRequest request = new PunishmentAcknowledgeRequest(
                    ban.getId(),
                    playerUuid,
                    java.time.Instant.now().toString(),
                    true,
                    null
            );

            httpClient.acknowledgePunishment(request).thenAccept(response -> {
                if (debugMode) logger.info("Successfully acknowledged ban enforcement for punishment " + ban.getId());
            }).exceptionally(throwable -> {
                logger.severe("Failed to acknowledge ban enforcement for punishment " + ban.getId() + ": " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.severe("Error acknowledging ban enforcement for punishment " + ban.getId() + ": " + e.getMessage());
        }
    }

    public static void handlePendingIpLookups(ModlHttpClient httpClient, PlayerLoginResponse response, String minecraftUUID, String originalIp, CompletableFuture<JsonObject> originalIpInfoFuture, PluginLogger logger) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) return;

        for (String ip : response.getPendingIpLookups()) {
            CompletableFuture<JsonObject> ipInfoFuture = ip.equals(originalIp) && originalIpInfoFuture != null
                    ? originalIpInfoFuture
                    : IpApiClient.getIpInfo(ip);
            ipInfoFuture.thenAccept(ipInfo -> submitIpInfoIfSuccess(httpClient, minecraftUUID, ip, ipInfo, logger))
                    .exceptionally(throwable -> {
                        logger.warning("Failed to lookup IP " + ip + ": " + throwable.getMessage());
                        return null;
                    });
        }
    }

    private static void submitIpInfoIfSuccess(ModlHttpClient httpClient, String minecraftUUID, String ip, JsonObject ipInfo, PluginLogger logger) {
        if (ipInfo == null || !ipInfo.has("status") || !"success".equals(ipInfo.get("status").getAsString())) return;

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

    public static void handlePlayerJoin(
            UUID uuid, String playerName,
            Platform platform, Cache cache, LocaleManager localeManager,
            Staff2faService staff2faService, SyncService syncService) {

        cache.setOnline(uuid);

        if (staff2faService != null && staff2faService.isEnabled() && PermissionUtil.isStaff(uuid, cache)) staff2faService.onStaffJoin(uuid);

        if (PermissionUtil.isStaff(uuid, cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(uuid))) {
            String panelName = cache.getStaffDisplayName(uuid);
            if (panelName == null) panelName = playerName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.join",
                    Map.of("staff", panelName, "in-game-name", playerName, "server", platform.getServerName())));
        }

        syncService.deliverPendingNotifications(uuid);
    }

    public static void handlePlayerDisconnect(
            UUID uuid, String playerName,
            ModlHttpClient httpClient, Cache cache, Platform platform,
            LocaleManager localeManager, FreezeService freezeService,
            StaffChatService staffChatService, ChatManagementService chatManagementService,
            NetworkChatInterceptService networkChatInterceptService, Staff2faService staff2faService,
            ChatMessageCache chatMessageCache,
            VanishService vanishService, StaffModeService staffModeService, BridgeService bridgeService) {

        // Must compute before marking offline (which clears join time)
        long sessionDuration = cache.getSessionDuration(uuid);
        httpClient.playerDisconnect(new PlayerDisconnectRequest(uuid.toString(), sessionDuration));

        if (PermissionUtil.isStaff(uuid, cache)) {
            String displayName = cache.getDisplayName(uuid, playerName);
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.leave",
                    Map.of("staff", displayName, "in-game-name", playerName)));
            httpClient.reportStaffDisconnect(uuid.toString(), sessionDuration);
        }

        if (freezeService.isFrozen(uuid)) {
            platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification",
                    Map.of("player", playerName)));
            freezeService.removePlayer(uuid);
        }

        if (vanishService.isVanished(uuid)) {
            vanishService.unvanish(uuid);
            if (bridgeService != null) {
                String panelName = cache.getDisplayName(uuid, playerName);
                bridgeService.sendVanishExit(uuid.toString(), playerName, panelName);
            }
        }

        if (staffModeService.isInStaffMode(uuid)) {
            staffModeService.removePlayer(uuid);
            if (bridgeService != null) {
                String panelName = cache.getDisplayName(uuid, playerName);
                bridgeService.sendStaffModeExit(uuid.toString(), playerName, panelName);
            }
        }

        cache.setOffline(uuid);

        staffChatService.removePlayer(uuid);
        chatManagementService.removePlayer(uuid);
        networkChatInterceptService.removePlayer(uuid);
        if (staff2faService != null) staff2faService.removePlayer(uuid);
        cache.removePlayer(uuid);
        chatMessageCache.removePlayer(uuid.toString());
        ChatInputManager.clearOnDisconnect(uuid);
        IAmMutedCommand.clearOnDisconnect(uuid);
    }
}
