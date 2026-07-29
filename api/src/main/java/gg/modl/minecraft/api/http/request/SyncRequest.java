package gg.modl.minecraft.api.http.request;

import gg.modl.minecraft.api.http.ChatLogEntry;
import gg.modl.minecraft.api.http.CommandLogEntry;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value
public class SyncRequest {
    @NotNull String lastSyncTimestamp;
    @NotNull List<OnlinePlayer> onlinePlayers;
    @Nullable String serverName;
    @Nullable String serverInstanceId;
    @Nullable List<ChatLogEntry> chatLogs;
    @Nullable List<CommandLogEntry> commandLogs;
    @Nullable ServerStatus serverStatus;

    @Value
    public static class ServerStatus {
        int onlinePlayerCount;
        int maxPlayers;
        @Nullable String serverVersion;
        @Nullable String platformType;
        @Nullable String pluginVersion;
        long timestamp;
    }

    @Value
    public static class OnlinePlayer {
        @NotNull String uuid, username, ipAddress;
        long sessionDurationMs;
    }
}
