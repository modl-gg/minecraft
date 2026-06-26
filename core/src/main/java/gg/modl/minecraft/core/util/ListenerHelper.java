package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.request.PunishmentAcknowledgeRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.boot.StartupClient;

import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.sync.SyncService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;
import java.time.Instant;

public final class ListenerHelper {

    private ListenerHelper() {}

    /**
     * Awaits both the ip-info and skin-hash lookups with a bounded timeout, falling back to null per-field
     * on timeout/failure (so a slow Mojang/ip-api call never blocks or denies login), then builds and stamps
     * the PlayerLoginRequest. Used by the Bungee and Velocity login paths to avoid the getNow(null) bug that
     * dropped skinHash/ipInfo on virtually every login.
     */
    public static PlayerLoginRequest buildLoginRequest(String uuid, String username, String ipAddress, String serverName,
                                                       CompletableFuture<Map<String, Object>> ipInfoFuture,
                                                       CompletableFuture<String> skinHashFuture,
                                                       long awaitTimeoutSeconds, PluginLogger logger) {
        Map<String, Object> ipInfo = awaitQuietly(ipInfoFuture, awaitTimeoutSeconds, "ipInfo", logger);
        String skinHash = awaitQuietly(skinHashFuture, awaitTimeoutSeconds, "skinHash", logger);

        PlayerLoginRequest request = new PlayerLoginRequest(uuid, username, ipAddress, skinHash, serverName, ipInfo);
        request.setServerInstanceId(StartupClient.getServerInstanceId());
        return request;
    }

    private static <T> T awaitQuietly(CompletableFuture<T> future, long timeoutSeconds, String label, PluginLogger logger) {
        if (future == null) return null;
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            if (logger != null) logger.warning("Login " + label + " lookup did not complete in time: " + e.getMessage());
            return null;
        }
    }

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
                    Instant.now().toString(),
                    null,
                    true
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

    public static void handlePendingIpLookups(ModlHttpClient httpClient, PlayerLoginResponse response, String minecraftUUID, String originalIp, CompletableFuture<Map<String, Object>> originalIpInfoFuture, PluginLogger logger) {
        if (response.getPendingIpLookups() == null || response.getPendingIpLookups().isEmpty()) return;

        for (String ip : response.getPendingIpLookups()) {
            CompletableFuture<Map<String, Object>> ipInfoFuture = ip.equals(originalIp) && originalIpInfoFuture != null
                    ? originalIpInfoFuture
                    : IpApiClient.getIpInfo(ip);
            ipInfoFuture.thenAccept(ipInfo -> submitIpInfoIfSuccess(httpClient, minecraftUUID, ip, ipInfo, logger))
                    .exceptionally(throwable -> {
                        logger.warning("Failed to lookup IP " + ip + ": " + throwable.getMessage());
                        return null;
                    });
        }
    }

    private static void submitIpInfoIfSuccess(ModlHttpClient httpClient, String minecraftUUID, String ip, Map<String, Object> ipInfo, PluginLogger logger) {
        if (ipInfo == null || !"success".equals(ipInfo.get("status"))) return;

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

    public static void handlePlayerJoin(
            UUID uuid, String playerName,
            Platform platform, Cache cache, LocaleManager localeManager,
            Staff2faService staff2faService, SyncService syncService) {

        // The player may have disconnected during async login (Fabric login-executor path) before this
        // main-thread join completed; don't register a profile / broadcast for an already-offline player.
        if (!platform.isOnline(uuid)) {
            return;
        }

        cache.getRegistry().createProfile(uuid);

        if (staff2faService != null && staff2faService.isEnabled() && PermissionUtil.isStaff(uuid, cache)) staff2faService.onStaffJoin(uuid);

        if (PermissionUtil.isStaff(uuid, cache)
                && (staff2faService == null || !staff2faService.isEnabled() || staff2faService.isAuthenticated(uuid))) {
            String panelName = cache.getStaffDisplayName(uuid);
            if (panelName == null) panelName = playerName;
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.join",
                    mapOf("staff", panelName, "in-game-name", playerName, "server", platform.getServerName())));
        }

        syncService.deliverPendingNotifications(uuid);
    }

    public static void handlePlayerDisconnect(
            UUID uuid, String playerName,
            ModlHttpClient httpClient, Cache cache, Platform platform,
            LocaleManager localeManager,
            ChatMessageCache chatMessageCache,
            BridgeService bridgeService,
            CachedProfileRegistry registry) {

        CachedProfile profile = registry.getProfile(uuid);
        long sessionDuration = profile != null ? profile.getSessionDuration() : 0;
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(uuid.toString(), sessionDuration);
        request.setServerInstanceId(StartupClient.getServerInstanceId());
        httpClient.playerDisconnect(request);

        if (PermissionUtil.isStaff(uuid, cache)) {
            String displayName = cache.getDisplayName(uuid, playerName);
            platform.staffBroadcast(localeManager.getMessage("staff_notifications.leave",
                    mapOf("staff", displayName, "in-game-name", playerName)));
            httpClient.reportStaffDisconnect(uuid.toString(), sessionDuration);
        }

        if (profile != null && profile.getFrozenByStaff() != null) {
            platform.staffBroadcast(localeManager.getMessage("freeze.logout_notification",
                    mapOf("player", playerName)));
        }

        if (profile != null && profile.isVanished() && bridgeService != null) {
            String panelName = cache.getDisplayName(uuid, playerName);
            bridgeService.sendVanishExit(uuid.toString(), playerName, panelName);
        }

        if (profile != null && profile.getStaffModeState() != StaffModeService.StaffModeState.OFF && bridgeService != null) {
            String panelName = cache.getDisplayName(uuid, playerName);
            bridgeService.sendStaffModeExit(uuid.toString(), playerName, panelName);
        }

        ReplayService replayService = platform.getReplayService();
        if (replayService != null) {
            replayService.onPlayerDisconnect(uuid);
        }

        registry.destroyProfile(uuid);
        cache.setOffline(uuid);
        chatMessageCache.removePlayer(uuid.toString());
        platform.getChatInputManager().clearOnDisconnect(uuid);
    }
}
