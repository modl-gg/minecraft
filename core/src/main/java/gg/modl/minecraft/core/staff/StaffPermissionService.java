package gg.modl.minecraft.core.staff;

import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.api.http.response.StaffPermissionsResponse;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StaffPermissionService {
    private final HttpClientHolder httpClientHolder;
    private final Cache cache;
    private final PluginLogger logger;
    private final boolean debugMode;

    public StaffPermissionService(HttpClientHolder httpClientHolder, Cache cache, PluginLogger logger, boolean debugMode) {
        this.httpClientHolder = httpClientHolder;
        this.cache = cache;
        this.logger = logger;
        this.debugMode = debugMode;
    }

    public CompletableFuture<Void> initialLoad() {
        return load(false);
    }

    public CompletableFuture<Void> reload() {
        return load(true);
    }

    private CompletableFuture<Void> load(boolean clearFirst) {
        if (debugMode) logger.info("Loading staff permissions...");
        ModlHttpClient httpClient = httpClientHolder.getClient();
        return httpClient.getStaffPermissions().thenAccept(response -> {
            if (clearFirst) cache.clearStaffPermissions();
            int loadedCount = 0;
            for (StaffPermissionsResponse.StaffMember staffMember : response.getData().getStaff()) {
                if (staffMember.getMinecraftUuid() != null) {
                    try {
                        UUID uuid = UUID.fromString(staffMember.getMinecraftUuid());
                        cache.cacheStaffPermissions(uuid, staffMember.getStaffUsername(), staffMember.getStaffId(), staffMember.getStaffRole(), staffMember.getPermissions());
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
