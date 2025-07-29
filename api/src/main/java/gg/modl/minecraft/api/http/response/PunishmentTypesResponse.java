package gg.modl.minecraft.api.http.response;

import lombok.Data;
import java.util.List;

@Data
public class PunishmentTypesResponse {
    private final int status;
    private final List<PunishmentTypeData> data;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
    
    @Data
    public static class PunishmentTypeData {
        private final int id;
        private final int ordinal;
        private final String name;
        private final String category;
        private final boolean isCustomizable;
        private final Object durations;
        private final Object points;
        private final Integer customPoints;
        private final Boolean canBeAltBlocking;
        private final Boolean canBeStatWiping;
        private final Boolean singleSeverityPunishment;
        private final String staffDescription;
        private final String playerDescription;
        private final Boolean permanentUntilSkinChange;
        private final Boolean permanentUntilNameChange;
    }
}