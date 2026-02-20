package gg.modl.minecraft.core;

import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
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
}

