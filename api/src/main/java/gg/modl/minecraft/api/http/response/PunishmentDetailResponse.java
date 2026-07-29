package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PunishmentDetailResponse extends StatusResponse {
    private PunishmentDetail punishment;
    private int status;

    @Override
    public boolean isSuccess() {
        return super.isSuccess() && punishment != null;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PunishmentDetail {
        private String id, playerUuid, playerName, issuerName, issued, started, type;
        private Map<String, Object> data;
        private List<Object> modifications, notes, evidence;
        private int typeOrdinal;
    }
}
