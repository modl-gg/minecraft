package gg.modl.minecraft.core.service.sync;

import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.staff.StaffPermissionService;
import gg.modl.minecraft.core.util.PluginLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static gg.modl.minecraft.core.util.Java8Collections.orTimeout;

class RefreshCoordinator {
    private static final long HTTP_TIMEOUT_SECONDS = 5;

    private final StaffPermissionService staffPermissionService;
    private final HttpClientHolder httpClientHolder;
    private final PluginLogger logger;
    private final boolean debugMode;
    private final List<SyncService.PunishmentTypesRefreshListener> punishmentTypesListeners = new CopyOnWriteArrayList<>();

    private volatile Long lastKnownStaffPermissionsTimestamp = null, lastKnownPunishmentTypesTimestamp = null;

    RefreshCoordinator(StaffPermissionService staffPermissionService, HttpClientHolder httpClientHolder,
                       PluginLogger logger, boolean debugMode) {
        this.staffPermissionService = staffPermissionService;
        this.httpClientHolder = httpClientHolder;
        this.logger = logger;
        this.debugMode = debugMode;
    }

    void addPunishmentTypesListener(SyncService.PunishmentTypesRefreshListener listener) {
        punishmentTypesListeners.add(listener);
    }

    void onSyncTimestamps(Long staffPermissionsUpdatedAt, Long punishmentTypesUpdatedAt) {
        refreshIfTimestampChanged(staffPermissionsUpdatedAt, lastKnownStaffPermissionsTimestamp,
                "Staff permissions", this::refreshStaffPermissions, ts -> lastKnownStaffPermissionsTimestamp = ts);
        refreshIfTimestampChanged(punishmentTypesUpdatedAt, lastKnownPunishmentTypesTimestamp,
                "Punishment types", this::refreshPunishmentTypes, ts -> lastKnownPunishmentTypesTimestamp = ts);
    }

    private void refreshIfTimestampChanged(Long newTimestamp, Long lastKnown, String label,
                                           Runnable refreshAction, Consumer<Long> updateLastKnown) {
        if (newTimestamp == null || newTimestamp.equals(lastKnown)) return;
        if (debugMode) logger.info(label + " changed (timestamp: " + newTimestamp + "), refreshing...");
        refreshAction.run();
        updateLastKnown.accept(newTimestamp);
    }

    void refreshStaffPermissions() {
        orTimeout(staffPermissionService.reload(), HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    void refreshPunishmentTypes() {
        orTimeout(httpClientHolder.getClient().getPunishmentTypes().thenAccept(response -> {
            if (!response.isSuccess()) return;
            for (SyncService.PunishmentTypesRefreshListener listener : punishmentTypesListeners) {
                try {
                    listener.onPunishmentTypesRefreshed(response.getData());
                } catch (Exception e) {
                    logger.warning("Error notifying punishment types listener: " + e.getMessage());
                }
            }
            if (debugMode) logger.info("Punishment types refreshed: " + response.getData().size() + " types");
        }).exceptionally(throwable -> {
            Throwable cause = throwable.getCause();
            if (cause instanceof PanelUnavailableException) logger.warning("Failed to refresh punishment types: Panel temporarily unavailable");
            else logger.warning("Failed to refresh punishment types: " + throwable.getMessage());
            return null;
        }), HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
