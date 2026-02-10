package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * V2 API sync request matching backend's SyncRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2SyncRequest {
    private String lastSyncTimestamp;
    private List<OnlinePlayer> onlinePlayers;
    private ServerStatus serverStatus;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OnlinePlayer {
        private String uuid;
        private String username;
        private String ipAddress;
        private long sessionDurationMs;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServerStatus {
        private int onlinePlayerCount;
        private int maxPlayers;
        private String serverVersion;
        private String timestamp;
    }
}
