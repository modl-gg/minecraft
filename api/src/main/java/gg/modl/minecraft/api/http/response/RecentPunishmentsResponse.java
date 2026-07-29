package gg.modl.minecraft.api.http.response;

import com.google.gson.annotations.SerializedName;
import gg.modl.minecraft.api.Evidence;
import gg.modl.minecraft.api.Modification;
import gg.modl.minecraft.api.Note;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter @NoArgsConstructor @AllArgsConstructor
public class RecentPunishmentsResponse extends StatusResponse {
    private List<RecentPunishment> punishments;
    private int status;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
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
