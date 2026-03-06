package gg.modl.minecraft.velocity;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.request.PlayerLoginRequest;
import gg.modl.minecraft.api.http.response.PlayerLoginResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatManagementService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.service.MaintenanceService;
import gg.modl.minecraft.core.service.NetworkChatInterceptService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffChatService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.VanishService;
import gg.modl.minecraft.core.sync.SyncService;
import gg.modl.minecraft.core.util.IpApiClient;
import gg.modl.minecraft.core.util.ListenerHelper;
import gg.modl.minecraft.core.util.PermissionUtil;
import gg.modl.minecraft.core.util.PunishmentMessages;
import gg.modl.minecraft.core.util.PunishmentMessages.MessageContext;
import gg.modl.minecraft.core.util.WebPlayer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class JoinListener {

    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final Logger logger;
    private final ChatMessageCache chatMessageCache;
    private final Platform platform;
    private final SyncService syncService;
    private final LocaleManager localeManager;
    private final boolean debugMode;
    private final StaffChatService staffChatService;
    private final ChatManagementService chatManagementService;
    private final MaintenanceService maintenanceService;
    private final FreezeService freezeService;
    private final NetworkChatInterceptService networkChatInterceptService;
    private final Staff2faService staff2faService;
    private final VanishService vanishService;
    private final StaffModeService staffModeService;
    private final BridgeService bridgeService;

    private static final long LOGIN_TIMEOUT_SECONDS = 5;

    private ModlHttpClient getHttpClient() {
        return httpClientHolder.getClient();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        String ipAddress = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        CompletableFuture<JsonObject> ipInfoFuture = IpApiClient.getIpInfo(ipAddress);
        CompletableFuture<String> skinHashFuture = WebPlayer.get(event.getPlayer().getUniqueId())
                .thenApply(wp -> wp != null && wp.valid() ? wp.skin() : null)
                .exceptionally(t -> null);

        // getNow() avoids blocking -- backend will request IP lookup via pendingIpLookups if null
        JsonObject ipInfo = null;
        String skinHash = null;
        try {
            ipInfo = ipInfoFuture.getNow(null);
            skinHash = skinHashFuture.getNow(null);
        } catch (Exception ignored) {}

        PlayerLoginRequest request = new PlayerLoginRequest(
                event.getPlayer().getUniqueId().toString(),
                event.getPlayer().getUsername(),
                ipAddress, skinHash, ipInfo, platform.getServerName()
        );

        try {
            PlayerLoginResponse response = getHttpClient().playerLogin(request).get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logLoginResponse(event, response);
            ListenerHelper.handlePendingIpLookups(getHttpClient(), response, event.getPlayer().getUniqueId().toString(), ipAddress, ipInfoFuture, java.util.logging.Logger.getLogger(JoinListener.class.getName()));
            processLoginResponse(event, response);
        } catch (PanelUnavailableException e) {
            logger.warn("Panel 502 during login check for {} - blocking login for safety", event.getPlayer().getUsername());
            denyLogin(event, Component.text("Unable to verify ban status. Login temporarily restricted for safety.").color(NamedTextColor.RED));
        } catch (java.util.concurrent.TimeoutException e) {
            logger.warn("Login check timed out for {} - blocking login for safety", event.getPlayer().getUsername());
            denyLogin(event, Component.text("Login verification timed out. Please try again.").color(NamedTextColor.RED));
        } catch (Exception e) {
            // Allow login on other errors to prevent false kicks
            logger.error("Failed to check punishments for " + event.getPlayer().getUsername() + " - allowing login as fallback", e);
            event.setResult(ResultedEvent.ComponentResult.allowed());
        }
    }

    private void processLoginResponse(LoginEvent event, PlayerLoginResponse response) {
        if (response.hasActiveBan()) {
            SimplePunishment ban = response.getActiveBan();
            denyLogin(event, Colors.get(PunishmentMessages.formatBanMessage(ban, localeManager, MessageContext.LOGIN)));
            if (debugMode) logger.info("Denied login for {} due to active ban: {}", event.getPlayer().getUsername(), ban.getDescription());
            if (!ban.isStarted()) {
                ListenerHelper.acknowledgeBanEnforcement(getHttpClient(), ban, event.getPlayer().getUniqueId().toString(), debugMode, java.util.logging.Logger.getLogger(JoinListener.class.getName()));
            }
        } else if (syncService.isStatWipeAvailable() && response.hasPendingStatWipes()) {
            denyLogin(event, Colors.get(localeManager.getMessage("stat_wipe.kick_message")));
            for (SyncResponse.PendingStatWipe statWipe : response.getPendingStatWipes()) syncService.executeStatWipeFromLogin(statWipe);
        } else if (maintenanceService.isEnabled() && !maintenanceService.canJoin(event.getPlayer().getUniqueId(), cache)) {
            denyLogin(event, Colors.get(localeManager.getMessage("maintenance.login_denied")));
        } else {
            cacheLoginData(event, response);
            event.setResult(ResultedEvent.ComponentResult.allowed());
            if (debugMode) logger.info("Allowed login for {}", event.getPlayer().getUsername());
        }
    }

    private void cacheLoginData(LoginEvent event, PlayerLoginResponse response) {
        if (response.hasActiveMute()) {
            SimplePunishment mute = response.getActiveMute();
            cache.cacheMute(event.getPlayer().getUniqueId(), mute);
            if (debugMode) logger.info("Cached active mute for {}: {}", event.getPlayer().getUsername(), mute.getDescription());
        }

        if (response.hasNotifications()) {
            for (Map<String, Object> notificationData : response.getPendingNotifications()) {
                SyncResponse.PlayerNotification notification = ListenerHelper.mapToPlayerNotification(notificationData, java.util.logging.Logger.getLogger(JoinListener.class.getName()));
                if (notification != null) cache.cacheNotification(event.getPlayer().getUniqueId(), notification);
            }
        }
    }

    private void logLoginResponse(LoginEvent event, PlayerLoginResponse response) {
        if (!debugMode) return;
        logger.info("Login response for {}: status={}, punishments={}", event.getPlayer().getUsername(), response.getStatus(), response.getActivePunishments());
        if (response.getActivePunishments() != null) {
            for (SimplePunishment p : response.getActivePunishments()) {
                logger.info("Punishment: type='{}', isBan={}, isMute={}, started={}, id={}", p.getType(), p.isBan(), p.isMute(), p.isStarted(), p.getId());
            }
        }
        logger.info("Login response for {}: hasBan={}, hasMute={}", event.getPlayer().getUsername(), response.hasActiveBan(), response.hasActiveMute());
    }

    private void denyLogin(LoginEvent event, Component message) {
        event.setResult(ResultedEvent.ComponentResult.denied(message));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        ListenerHelper.handlePlayerJoin(uuid, event.getPlayer().getUsername(),
                platform, cache, localeManager, staff2faService, syncService);

        String texture = platform.getPlayerSkinTexture(uuid);
        if (texture != null) cache.cacheSkinTexture(uuid, texture);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ListenerHelper.handlePlayerDisconnect(
                event.getPlayer().getUniqueId(), event.getPlayer().getUsername(),
                getHttpClient(), cache, platform, localeManager, freezeService,
                staffChatService, chatManagementService, networkChatInterceptService,
                staff2faService, chatMessageCache,
                vanishService, staffModeService, bridgeService);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();
        getHttpClient().updatePlayerServer(uuid.toString(), serverName)
                .exceptionally(throwable -> {
                    logger.warn("Failed to update server for {}: {}", event.getPlayer().getUsername(), throwable.getMessage());
                    return null;
                });

        if (!PermissionUtil.isStaff(uuid, cache)) return;
        String inGameName = event.getPlayer().getUsername();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.switch",
                java.util.Map.of("staff", panelName, "in-game-name", inGameName, "server", serverName)));
    }

}
