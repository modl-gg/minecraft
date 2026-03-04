package gg.modl.minecraft.core.service;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.util.PermissionUtil;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing server maintenance mode.
 * <p>
 * When maintenance mode is enabled, only staff members may remain on
 * or join the server. Non-staff players are kicked with a configurable
 * message.
 */
public class MaintenanceService {

    private volatile boolean maintenanceEnabled = false;

    /**
     * Enable maintenance mode.
     * <p>
     * All online non-staff players are kicked using the configured kick
     * message. The kick is executed on the game thread to satisfy
     * platform requirements (e.g. Paper's AsyncCatcher).
     *
     * @param platform    The platform instance for player operations
     * @param cache       The cache for staff lookups
     * @param kickMessage The formatted kick message to send to non-staff players
     */
    public void enable(Platform platform, Cache cache, String kickMessage) {
        this.maintenanceEnabled = true;

        // Kick all non-staff players
        Collection<AbstractPlayer> onlinePlayers = platform.getOnlinePlayers();

        for (AbstractPlayer player : onlinePlayers) {
            UUID uuid = player.getUuid();
            if (!PermissionUtil.isStaff(uuid, cache)) {
                platform.runOnGameThread(() -> platform.kickPlayer(player, kickMessage));
            }
        }
    }

    /**
     * Disable maintenance mode.
     */
    public void disable() {
        this.maintenanceEnabled = false;
    }

    /**
     * Check whether maintenance mode is currently enabled.
     */
    public boolean isEnabled() {
        return maintenanceEnabled;
    }

    /**
     * Determine whether a player is allowed to join.
     *
     * @param uuid  The player's UUID
     * @param cache The cache for staff lookups
     * @return {@code true} if maintenance is disabled or the player is staff
     */
    public boolean canJoin(UUID uuid, Cache cache) {
        if (!maintenanceEnabled) {
            return true;
        }
        return PermissionUtil.isStaff(uuid, cache);
    }
}
