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
        private final Boolean permanentUntilUsernameChange;

        /**
         * Check if this punishment type is a kick.
         * Kicks are instant punishments with no duration.
         */
        public boolean isKick() {
            return "KICK".equalsIgnoreCase(category) || ordinal == 0;
        }

        /**
         * Check if this punishment type is a ban.
         */
        public boolean isBan() {
            return "BAN".equalsIgnoreCase(category);
        }

        /**
         * Check if this punishment type is a mute.
         */
        public boolean isMute() {
            return "MUTE".equalsIgnoreCase(category);
        }
    }
}