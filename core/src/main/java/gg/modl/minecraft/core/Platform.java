package gg.modl.minecraft.core;

import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.DatabaseProvider;
import gg.modl.minecraft.core.util.PluginLogger;

import java.io.File;
import java.util.UUID;

public interface Platform extends PlatformMessaging, PlatformPlayers, PlatformCommands, Scheduler {
    PluginLogger getLogger();
    void log(String msg);
    String getServerVersion();
    String getPlatformType();
    String getServerName();
    File getDataFolder();
    DatabaseProvider createLiteBansDatabaseProvider();

    default String getPlayerServer(UUID uuid) { return getServerName(); }
    default void connectToServer(UUID player, String serverName) {}
}
