package gg.modl.minecraft.core.session;

import gg.modl.minecraft.core.PluginServices;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.request.PlayerDisconnectRequest;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.boot.StartupClient;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;
import gg.modl.minecraft.core.cache.LoginCache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.ChatMessageCache;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import gg.modl.minecraft.core.service.sync.SyncService;
import gg.modl.minecraft.core.staff.PermissionUtil;

import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class PlayerSessionService {
    private final Platform platform;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final SyncService syncService;
    private final HttpClientHolder httpClientHolder;
    private final LoginCache loginCache;
    private final ChatMessageCache chatMessageCache;
    private final BridgeService bridgeService;
    private final CachedProfileRegistry registry;

    public PlayerSessionService(Platform platform, Cache cache, LocaleManager localeManager,
                                Staff2faService staff2faService, SyncService syncService,
                                HttpClientHolder httpClientHolder, LoginCache loginCache,
                                ChatMessageCache chatMessageCache, BridgeService bridgeService,
                                CachedProfileRegistry registry) {
        this.platform = platform;
        this.cache = cache;
        this.localeManager = localeManager;
        this.staff2faService = staff2faService;
        this.syncService = syncService;
        this.httpClientHolder = httpClientHolder;
        this.loginCache = loginCache;
        this.chatMessageCache = chatMessageCache;
        this.bridgeService = bridgeService;
        this.registry = registry;
    }

    public void handlePlayerJoin(UUID uuid, String playerName) {
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

    public void handlePlayerDisconnect(UUID uuid, String playerName) {
        ModlHttpClient httpClient = httpClientHolder.getClient();

        CachedProfile profile = registry.getProfile(uuid);
        long sessionDuration = profile != null ? profile.getSessionDuration() : 0;
        PlayerDisconnectRequest request = new PlayerDisconnectRequest(uuid.toString(), sessionDuration,
                StartupClient.getServerInstanceId());
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

        ReplayService replayService = PluginServices.replay();
        if (replayService != null) {
            replayService.onPlayerDisconnect(uuid);
        }

        registry.destroyProfile(uuid);
        cache.setOffline(uuid);
        loginCache.invalidateLoginResult(uuid);
        chatMessageCache.removePlayer(uuid.toString());
        PluginServices.chatInput().clearOnDisconnect(uuid);
    }
}
