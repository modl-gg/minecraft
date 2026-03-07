package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.PunishmentTypeRegistry;
import lombok.Data;

import java.util.List;

@Data
public class PunishmentTypesResponse {
    private final List<PunishmentTypeData> data;
    private final int status;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
    
    @Data
    public static class PunishmentTypeData {
        private final String name, category, staffDescription, playerDescription;
        private final Object durations, points;
        private final Integer customPoints;
        private final Boolean canBeAltBlocking, canBeStatWiping, singleSeverityPunishment,
                permanentUntilSkinChange, permanentUntilUsernameChange;
        private final int id, ordinal;
        private final boolean isCustomizable;

        public boolean isKick() {
            return "KICK".equalsIgnoreCase(category) || ordinal == PunishmentTypeRegistry.ORDINAL_KICK;
        }

        public boolean isBan() {
            return "BAN".equalsIgnoreCase(category);
        }

        public boolean isMute() {
            return "MUTE".equalsIgnoreCase(category);
        }
    }
}