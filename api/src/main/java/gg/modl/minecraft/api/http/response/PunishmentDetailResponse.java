package gg.modl.minecraft.api.http.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PunishmentDetailResponse {
    private PunishmentDetail punishment;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300 && punishment != null;
    }

    @Data
    public static class PunishmentDetail {
        private String id, playerUuid, playerName, issuerName, issued, started, type;
        private Map<String, Object> data;
        private List<Object> modifications, notes, evidence;
        private int typeOrdinal;
    }
}
