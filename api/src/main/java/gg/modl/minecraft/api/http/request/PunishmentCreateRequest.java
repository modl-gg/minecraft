package gg.modl.minecraft.api.http.request;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PunishmentCreateRequest {
    private final String targetUuid, issuerName, issuerId, reason, severity, status;
    private @SerializedName("type_ordinal") final int typeOrdinal;
    private final Long duration;
    private final Map<String, Object> data;
    private final List<String> notes, attachedTicketIds;
}