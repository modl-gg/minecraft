package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.PunishmentTypeClassifier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PunishmentTypesResponse extends StatusResponse {
    private List<PunishmentTypeData> data;
    private int status;

    @Getter @AllArgsConstructor
    public static class PunishmentTypeData {
        private final String name, category, staffDescription, playerDescription;
        private final Object durations, points;
        private final Integer customPoints;
        private final Boolean canBeAltBlocking, canBeStatWiping, singleSeverityPunishment,
                permanentUntilSkinChange, permanentUntilUsernameChange;
        private final int id, ordinal;
        private final boolean isCustomizable;

        public boolean isKick() {
            return "KICK".equalsIgnoreCase(category) || ordinal == PunishmentTypeClassifier.ORDINAL_KICK;
        }

        public boolean isBan() {
            return "BAN".equalsIgnoreCase(category);
        }

        public boolean isMute() {
            return "MUTE".equalsIgnoreCase(category);
        }
    }
}
