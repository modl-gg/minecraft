package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

class StaffSyncProcessor {
    private final Platform platform;
    private final Cache cache;
    private final PluginLogger logger;
    private final LocaleManager localeManager;
    private final Staff2faService staff2faService;
    private final boolean debugMode;

    StaffSyncProcessor(Platform platform, Cache cache, PluginLogger logger, LocaleManager localeManager,
                       Staff2faService staff2faService, boolean debugMode) {
        this.platform = platform;
        this.cache = cache;
        this.logger = logger;
        this.localeManager = localeManager;
        this.staff2faService = staff2faService;
        this.debugMode = debugMode;
    }

    void reconcileActiveStaff(List<SyncResponse.ActiveStaffMember> staffMembers) {
        Set<UUID> activeStaffUuids = new HashSet<>();
        for (SyncResponse.ActiveStaffMember staffMember : staffMembers) {
            processActiveStaffMember(staffMember);
            try {
                activeStaffUuids.add(UUID.fromString(staffMember.getMinecraftUuid()));
            } catch (IllegalArgumentException ignored) {}
        }
        evictStaleStaff(activeStaffUuids);
    }

    void processActiveStaffMember(SyncResponse.ActiveStaffMember staffMember) {
        try {
            UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
            AbstractPlayer player = platform.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                handle2faForStaffMember(uuid, player, staffMember);
                updateStaffMemberCache(uuid, staffMember);
            }
        } catch (Exception e) {
            logger.warning("Error processing staff member data: " + e.getMessage());
        }
    }

    private void evictStaleStaff(Set<UUID> activeStaffUuids) {
        for (CachedProfile profile : cache.getRegistry().getAllProfiles()) {
            if (profile.getStaffMember() != null && !activeStaffUuids.contains(profile.getUuid())) {
                profile.setStaffMember(null);
                cache.removeStaffPermissions(profile.getUuid());
                if (debugMode) logger.info("Evicted stale staff data for " + profile.getUuid());
            }
        }
    }

    private void handle2faForStaffMember(UUID uuid, AbstractPlayer player, SyncResponse.ActiveStaffMember staffMember) {
        if (staff2faService == null || !staff2faService.isEnabled()) return;
        if (!staff2faService.isAwaitingVerification(uuid)) return;

        if (Boolean.TRUE.equals(staffMember.getTwoFactorSessionValid())) {
            staff2faService.handleVerification(uuid);
            platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.auto_verified"));
            broadcastStaffJoin(uuid, player);
        } else {
            if (staff2faService.markNotified(uuid)) {
                platform.sendMessage(uuid, localeManager.getMessage("staff_2fa.not_verified"));
            }
        }
    }

    private void broadcastStaffJoin(UUID uuid, AbstractPlayer player) {
        String inGameName = player.getUsername();
        String panelName = cache.getStaffDisplayName(uuid);
        if (panelName == null) panelName = inGameName;
        platform.staffBroadcast(localeManager.getMessage("staff_notifications.join",
                mapOf("staff", panelName, "in-game-name", inGameName, "server", platform.getServerName())));
    }

    private void updateStaffMemberCache(UUID uuid, SyncResponse.ActiveStaffMember staffMember) {
        CachedProfile profile = cache.getPlayerProfile(uuid);
        if (profile == null) return;

        SyncResponse.ActiveStaffMember existing = profile.getStaffMember();
        boolean isNew = existing == null;
        boolean permissionsChanged = existing != null && !existing.getPermissions().equals(staffMember.getPermissions());

        profile.setStaffMember(staffMember);

        if (debugMode && (isNew || permissionsChanged)) {
            logger.info(String.format("Staff member data %s for %s (%s) - Role: %s, Permissions: %s",
                    isNew ? "loaded" : "updated",
                    staffMember.getMinecraftUsername(), staffMember.getStaffUsername(),
                    staffMember.getStaffRole(), staffMember.getPermissions()));
        }
    }
}
