package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public final class ServerSwitchHandler {
    private ServerSwitchHandler() {}
    public static void handleServerSwitch(
            UUID uuid, String username, String serverName,
            ModlHttpClient httpClient, Cache cache,
            LocaleManager localeManager, Platform platform) {

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
