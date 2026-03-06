package gg.modl.minecraft.core;

import co.aikar.commands.CommandManager;
import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.BridgeService;
import gg.modl.minecraft.core.service.Staff2faService;
import gg.modl.minecraft.core.service.StaffModeService;

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
    Collection<AbstractPlayer> getOnlinePlayers();
    AbstractPlayer getPlayer(UUID uuid);
    int getMaxPlayers();
    String getServerVersion();
    void runOnMainThread(Runnable task);

    // Default: execute directly (safe on BungeeCord and Velocity)
    default void runOnGameThread(Runnable task) {
        task.run();
    }

    void kickPlayer(AbstractPlayer player, String reason);
    String getServerName();
    default String getPlayerServer(UUID uuid) { return getServerName(); }
    File getDataFolder();
    DatabaseProvider createLiteBansDatabaseProvider();
    void log(String msg);

    default void staffChatBroadcast(String message) {
        staffBroadcast(message);
    }

    default void connectToServer(UUID player, String serverName) {}
    default void dispatchPlayerCommand(UUID uuid, String command) {}
    default void dispatchConsoleCommand(String command) {}
    default String getPlayerSkinTexture(UUID uuid) { return null; }

    Cache getCache();
    void setCache(Cache cache);
    LocaleManager getLocaleManager();
    void setLocaleManager(LocaleManager localeManager);
    StaffModeService getStaffModeService();
    void setStaffModeService(StaffModeService staffModeService);
    BridgeService getBridgeService();
    void setBridgeService(BridgeService bridgeService);
    void setStaff2faService(Staff2faService staff2faService);
}

