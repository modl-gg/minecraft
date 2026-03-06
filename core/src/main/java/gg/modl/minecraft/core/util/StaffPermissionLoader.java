package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.impl.cache.Cache;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StaffPermissionLoader {
    public static CompletableFuture<Void> load(ModlHttpClient httpClient, Cache cache, PluginLogger logger, boolean debugMode, boolean clearFirst) {
        if (debugMode) logger.info("Loading staff permissions...");
        return httpClient.getStaffPermissions().thenAccept(response -> {
            if (clearFirst) cache.clearStaffPermissions();
            int loadedCount = 0;
            for (var staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffUsername(), staffMember.getStaffRole(), staffMember.getPermissions());
                        loadedCount++;
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID for staff member: " + staffMember.getMinecraftUuid());
                    }
                }
            }
            if (debugMode) logger.info("Staff permissions loaded: " + loadedCount + " staff members");
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            if (cause instanceof PanelUnavailableException) logger.warning("Failed to load staff permissions: Panel temporarily unavailable");
            else logger.warning("Failed to load staff permissions: " + throwable.getMessage());
            return null;
        });
    }
}
