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

@Data @NoArgsConstructor @AllArgsConstructor
public class RecentPunishmentsResponse {
    private List<RecentPunishment> punishments;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RecentPunishment {
        private String playerName, playerUuid, id, issuerName;
        private Date issued, started;
        private @SerializedName("type") String type;
        private @SerializedName("typeOrdinal") Integer typeOrdinal;
        private @SerializedName("modifications") List<Modification> modifications;
        private @SerializedName("notes") List<Note> notes;
        private @SerializedName("evidence") List<Evidence> evidence;
        private @SerializedName("attachedTicketIds") List<String> attachedTicketIds;
        private @SerializedName("data") Map<String, Object> data;

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
