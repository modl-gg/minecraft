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

    private @Nullable List<ChatLogEntry> chatLogs;

    private @Nullable List<CommandLogEntry> commandLogs;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OnlinePlayer {
        private @NotNull String uuid;

        private @NotNull String username;

        private @NotNull String ipAddress;

        private long sessionDurationMs;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ServerStatus {
        private int onlinePlayerCount;
        private int maxPlayers;

        private @NotNull String serverVersion;

        private @NotNull String timestamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChatLogEntry {
        private String uuid;
        private String username;
        private String message;
        private long timestamp;
        private String server;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CommandLogEntry {
        private String uuid;
        private String username;
        private String command;
        private long timestamp;
        private String server;
    }
}
