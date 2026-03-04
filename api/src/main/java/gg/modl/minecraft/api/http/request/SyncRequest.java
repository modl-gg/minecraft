package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncRequest {
    @NotNull
    private String lastSyncTimestamp;

    @NotNull
    private List<OnlinePlayer> onlinePlayers;

    @NotNull
    private ServerStatus serverStatus;

    @Nullable
    private String serverName;

    @Nullable
    private List<ChatLogEntry> chatLogs;

    @Nullable
    private List<CommandLogEntry> commandLogs;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OnlinePlayer {
        @NotNull
        private String uuid;

        @NotNull
        private String username;

        @NotNull
        private String ipAddress;

        private long sessionDurationMs;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServerStatus {
        private int onlinePlayerCount;
        private int maxPlayers;

        @NotNull
        private String serverVersion;

        @NotNull
        private String timestamp;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatLogEntry {
        private String uuid;
        private String username;
        private String message;
        private long timestamp;
        private String server;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommandLogEntry {
        private String uuid;
        private String username;
        private String command;
        private long timestamp;
        private String server;
    }
}