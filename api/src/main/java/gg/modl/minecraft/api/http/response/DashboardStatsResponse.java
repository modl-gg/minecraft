package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse {
    private Stats stats;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private long unresolvedReports, unresolvedTickets, onlineStaff, onlinePlayers,
                activeBans, activeMutes, totalActivePunishments, totalPlayers;
    }
}
