package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlayerLookupResponse {
    private String message;
    private PlayerData data;
    private int status;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlayerData {
        private String minecraftUuid, currentUsername, firstSeen, lastSeen, currentServer, ipAddress, country,
                profileUrl, punishmentsUrl, ticketsUrl;
        private List<String> previousUsernames;
        private PunishmentStats punishmentStats;
        private List<RecentPunishment> recentPunishments;
        private List<RecentTicket> recentTickets;
        private boolean isOnline;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PunishmentStats {
        private String status;
        private int totalPunishments, activePunishments, bans, mutes, kicks, warnings, points;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RecentPunishment {
        private String id, type, issuer, issuedAt, expiresAt;
        private boolean isActive;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RecentTicket {
        private String id, title, category, status, createdAt, lastUpdated;
    }
}
