package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerLookupResponse {
    private int status;
    private String message;
    private PlayerData data;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerData {
        private String minecraftUuid;
        private String currentUsername;
        private List<String> previousUsernames;
        private String firstSeen;
        private String lastSeen;
        private String currentServer;
        private boolean isOnline;
        private String ipAddress;
        private String country;
        
        // Punishment statistics
        private PunishmentStats punishmentStats;
        
        // Recent activity
        private List<RecentPunishment> recentPunishments;
        private List<RecentTicket> recentTickets;
        
        // Profile URLs
        private String profileUrl;
        private String punishmentsUrl;
        private String ticketsUrl;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PunishmentStats {
        private int totalPunishments;
        private int activePunishments;
        private int bans;
        private int mutes;
        private int kicks;
        private int warnings;
        private int points;
        private String status; // low, medium, habitual
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentPunishment {
        private String id;
        private String type;
        private String issuer;
        private String issuedAt;
        private String expiresAt;
        private boolean isActive;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTicket {
        private String id;
        private String title;
        private String category;
        private String status;
        private String createdAt;
        private String lastUpdated;
    }
}