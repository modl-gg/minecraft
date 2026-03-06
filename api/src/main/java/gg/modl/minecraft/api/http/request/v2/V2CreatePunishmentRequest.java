package gg.modl.minecraft.api.http.request.v2;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * V2 API create punishment request matching backend's CreatePunishmentRequest record.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class V2CreatePunishmentRequest {
    private String targetUuid;
    private String issuerName;
    private @SerializedName("type_ordinal") int typeOrdinal;
    private String reason;
    private Long duration;
    private Map<String, Object> data;
    private List<String> notes;
    private List<String> attachedTicketIds;
    private String severity;
    private String status;
}
