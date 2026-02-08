package gg.modl.minecraft.api.http.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PunishmentDetailResponse {
    private int status;
    private PunishmentDetail punishment;

    public boolean isSuccess() {
        return status >= 200 && status < 300 && punishment != null;
    }

    @Data
    public static class PunishmentDetail {
        private String id;
        private String playerUuid;
        private String playerName;
        private String issuerName;
        private String issued;
        private String started;
        private int typeOrdinal;
        private String type;
        private Map<String, Object> data;
        private List<Object> modifications;
        private List<Object> notes;
        private List<Object> evidence;
    }
}
