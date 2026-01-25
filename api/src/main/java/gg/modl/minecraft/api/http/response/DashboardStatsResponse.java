package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private int status;
    private Stats stats;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private long unresolvedReports;
        private long unresolvedTickets;
        private long onlineStaff;
        private long onlinePlayers;
        private long activeBans;
        private long activeMutes;
        private long totalActivePunishments;
        private long totalPlayers;
    }
}
