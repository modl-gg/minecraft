package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SyncRequest {
    private @NotNull String lastSyncTimestamp;
    private @NotNull List<OnlinePlayer> onlinePlayers;
    private @NotNull ServerStatus serverStatus;
    private @Nullable String serverName;
    private transient @Nullable List<ChatLogEntry> chatLogs;
    private transient @Nullable List<CommandLogEntry> commandLogs;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OnlinePlayer {
        private @NotNull String uuid, username, ipAddress;

        private long sessionDurationMs;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ServerStatus {
        private @NotNull String serverVersion, timestamp;

        private int onlinePlayerCount, maxPlayers;

        private @Nullable String platformType;

        private @Nullable String pluginVersion;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChatLogEntry {
        private String uuid, username, message, server;
        private long timestamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CommandLogEntry {
        private String uuid, username, command, server;
        private long timestamp;
    }
}
