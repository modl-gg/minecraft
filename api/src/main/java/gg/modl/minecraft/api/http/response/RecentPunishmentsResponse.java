package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentPunishmentsResponse {
    private int status;
    private List<RecentPunishment> punishments;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentPunishment {
        private String id;
        private String type;
        private String playerName;
        private String playerUuid;
        private String issuerName;
        private Date issuedAt;
        private String reason;
        private boolean active;
    }
}
