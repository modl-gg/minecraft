package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class DashboardStatsResponse extends StatusResponse {
    private Stats stats;
    private int status;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class Stats {
        private long unresolvedReports, unresolvedTickets, onlineStaff, onlinePlayers,
                activeBans, activeMutes, totalActivePunishments, totalPlayers;
    }
}
