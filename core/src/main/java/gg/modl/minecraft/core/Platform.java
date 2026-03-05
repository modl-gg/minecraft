package gg.modl.minecraft.core;

import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

public interface Platform {
    void broadcast(String string);
    void staffBroadcast(String string);
    void staffJsonBroadcast(String jsonMessage);
    void disconnect(UUID uuid, String message);
    void sendMessage(UUID uuid, String message);
    void sendJsonMessage(UUID uuid, String jsonMessage);
    boolean isOnline(UUID uuid);
    CommandManager<?,?,?,?,?,?> getCommandManager();
    AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang);
    AbstractPlayer getAbstractPlayer(String username, boolean queryMojang);
    CirrusPlayerWrapper getPlayerWrapper(UUID uuid);
    
    // New methods for sync service
    Collection<AbstractPlayer> getOnlinePlayers();
    AbstractPlayer getPlayer(UUID uuid);
    int getMaxPlayers();
    String getServerVersion();
    void runOnMainThread(Runnable task);

    /**
     * Run a task on the game/server thread. Only needed for operations that
     * truly require the game thread, such as kicking players on Spigot/Paper
     * (Paper's AsyncCatcher blocks player.kickPlayer() from async threads).
     * Default: execute directly (safe on BungeeCord and Velocity).
     */
    default void runOnGameThread(Runnable task) {
        task.run();
    }
    void kickPlayer(AbstractPlayer player, String reason);
    String getServerName();
    default String getPlayerServer(UUID uuid) { return getServerName(); }
    File getDataFolder();
    
    /**
     * Create a DatabaseProvider using LiteBans API if available
     * @return DatabaseProvider or null if LiteBans is not available
     */
    DatabaseProvider createLiteBansDatabaseProvider();

    void log(String msg);

    /**
     * Broadcast a message to all staff members who have notifications enabled.
     * Only sends to staff with staffNotificationsEnabled == true.
     * @param message The message to send (color codes already translated)
     */
    default void staffChatBroadcast(String message) {
        // Default: delegate to staffBroadcast which sends to all staff
        staffBroadcast(message);
    }

    /**
     * Request the proxy to send a player to a specific server.
     * Only functional on BungeeCord/Velocity. No-op on Spigot standalone.
     * @param player The player UUID to connect
     * @param serverName The target server name
     */
    default void connectToServer(UUID player, String serverName) {
        // No-op on standalone Spigot
    }

    /**
     * Dispatch a command as a player. Used by bridge callbacks (e.g., OPEN_STAFF_MENU).
     */
    default void dispatchPlayerCommand(UUID uuid, String command) {}

    /**
     * Dispatch a command as the console. Used by bridge PROXY_CMD forwarding.
     */
    default void dispatchConsoleCommand(String command) {}

    /**
     * Get the base64 skin texture value for an online player using native platform APIs.
     * Returns null if the player is offline or texture is unavailable.
     * @param uuid The player's UUID
     * @return Base64 encoded texture value, or null
     */
    default String getPlayerSkinTexture(UUID uuid) { return null; }

    /**
     * Get the cache instance for permission and data caching
     * @return The cache instance
     */
    Cache getCache();

    /**
     * Set the cache instance (called by PluginLoader after initialization)
     * @param cache The cache instance
     */
    void setCache(Cache cache);

    /**
     * Get the locale manager for message localization
     * @return The locale manager instance
     */
    LocaleManager getLocaleManager();

    /**
     * Set the locale manager (called by PluginLoader after initialization)
     * @param localeManager The locale manager instance
     */
    void setLocaleManager(LocaleManager localeManager);

    /**
     * Get the staff mode service for managing staff mode state
     * @return The staff mode service instance
     */
    StaffModeService getStaffModeService();

    /**
     * Set the staff mode service (called by PluginLoader after initialization)
     * @param staffModeService The staff mode service instance
     */
    void setStaffModeService(StaffModeService staffModeService);

    /**
     * Get the bridge service for cross-server communication
     * @return The bridge service instance
     */
    BridgeService getBridgeService();

    /**
     * Set the bridge service (called by PluginLoader after initialization)
     * @param bridgeService The bridge service instance
     */
    void setBridgeService(BridgeService bridgeService);

    /**
     * Set the staff 2FA service (called by PluginLoader after initialization)
     * @param staff2faService The staff 2FA service instance
     */
    void setStaff2faService(Staff2faService staff2faService);

}

