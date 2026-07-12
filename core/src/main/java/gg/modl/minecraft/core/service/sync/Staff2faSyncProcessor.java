package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.List;
import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

class Staff2faSyncProcessor {
    private final Platform platform;
    private final Cache cache;
    private final PluginLogger logger;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;

    Staff2faSyncProcessor(Platform platform, Cache cache, PluginLogger logger, LocaleManager localeManager,
                          Staff2faService staff2faService) {
        this.platform = platform;
        this.cache = cache;
        this.logger = logger;
        this.localeManager = localeManager;
        this.staff2faService = staff2faService;
    }

    void processVerifications(List<SyncResponse.Staff2faVerification> verifications) {
        if (verifications == null) return;
        for (SyncResponse.Staff2faVerification verification : verifications) {
            try {
                UUID uuid = UUID.fromString(verification.getMinecraftUuid());
                if (!staff2faService.isAwaitingVerification(uuid)) continue;
                staff2faService.handleVerification(uuid);
                logger.info("[Sync] Staff 2FA verified for " + verification.getMinecraftUuid());
                notifyVerified(uuid);
            } catch (Exception e) {
                logger.warning("[Sync] Failed to process staff 2FA verification: " + e.getMessage());
            }
        }
    }

    private void notifyVerified(UUID uuid) {
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player == null) return;
        platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.verify_success"));
        String inGameName = player.getUsername();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.verified",
                mapOf("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }
}
