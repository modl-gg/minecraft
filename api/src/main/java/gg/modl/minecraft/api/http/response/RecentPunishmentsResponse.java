package gg.modl.minecraft.api.http.response;

import com.google.gson.annotations.SerializedName;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentPunishmentsResponse {
    private int status;
    private List<RecentPunishment> punishments;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /**
     * Recent punishment with full punishment data plus player info.
     * Uses same format as Punishment class from player profile.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentPunishment {
        // Player info (additional fields for recent punishments)
        private String playerName;
        private String playerUuid;

        // Full punishment data (same as Punishment class)
        private String id;
        private String issuerName;
        private Date issued;
        private Date started;

        @SerializedName("type")
        private String type;

        @SerializedName("typeOrdinal")
        private Integer typeOrdinal;

        @SerializedName("modifications")
        private List<Modification> modifications;

        @SerializedName("notes")
        private List<Note> notes;

        @SerializedName("evidence")
        private List<Evidence> evidence;

        @SerializedName("attachedTicketIds")
        private List<String> attachedTicketIds;

        @SerializedName("data")
        private Map<String, Object> data;

        public List<Modification> getModifications() {
            return modifications != null ? modifications : Collections.emptyList();
        }

        public List<Note> getNotes() {
            return notes != null ? notes : Collections.emptyList();
        }

        public List<Evidence> getEvidence() {
            return evidence != null ? evidence : Collections.emptyList();
        }

        public Map<String, Object> getData() {
            return data != null ? data : Collections.emptyMap();
        }

        public int getTypeOrdinal() {
            return typeOrdinal != null ? typeOrdinal : 0;
        }
    }
}
