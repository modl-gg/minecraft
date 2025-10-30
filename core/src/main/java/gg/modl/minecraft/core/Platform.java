package gg.modl.minecraft.core;

import co.aikar.commands.CommandManager;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

public interface Platform {
    void broadcast(String string);
    void staffBroadcast(String string);
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
    void kickPlayer(AbstractPlayer player, String reason);
    String getServerName();
    File getDataFolder();
    
    /**
     * Create a DatabaseProvider using LiteBans API if available
     * @return DatabaseProvider or null if LiteBans is not available
     */
    DatabaseProvider createLiteBansDatabaseProvider();
}

