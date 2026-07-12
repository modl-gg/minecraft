package gg.modl.minecraft.core.session;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.staff.PermissionUtil;

import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public final class ServerSwitchService {
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final LocaleManager localeManager;
    private final Platform platform;

    public ServerSwitchService(HttpClientHolder httpClientHolder, Cache cache, LocaleManager localeManager, Platform platform) {
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.localeManager = localeManager;
        this.platform = platform;
    }

    public void handleServerSwitch(UUID uuid, String username, String serverName) {
        ModlHttpClient httpClient = httpClientHolder.getClient();
        httpClient.updatePlayerServer(uuid.toString(), serverName)
                .exceptionally(throwable -> {
                    platform.getLogger().warning("Failed to update server for " + username + ": " + throwable.getMessage());
                    return null;
                });

        if (!PermissionUtil.isStaff(uuid, cache)) return;

        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = username;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.switch",
                mapOf("staff", panelName, "in-game-name", username, "server", serverName)));
    }
}
