package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.util.PermissionUtil;

import java.util.UUID;

public class MaintenanceService {
    private volatile boolean maintenanceEnabled = false;

    /** Enables maintenance and kicks all non-staff players on the game thread. */
    public void enable(Platform platform, Cache cache, String kickMessage) {
        this.maintenanceEnabled = true;
        for (AbstractPlayer player : platform.getOnlinePlayers()) {
            UUID uuid = player.getUuid();
            if (!PermissionUtil.isStaff(uuid, cache)) {
                platform.runOnGameThread(() -> platform.kickPlayer(player, kickMessage));
            }
        }
    }

    public void disable() {
        this.maintenanceEnabled = false;
    }

    public boolean isEnabled() {
        return maintenanceEnabled;
    }

    public boolean canJoin(UUID uuid, Cache cache) {
        if (!maintenanceEnabled) return true;
        return PermissionUtil.isStaff(uuid, cache);
    }
}
