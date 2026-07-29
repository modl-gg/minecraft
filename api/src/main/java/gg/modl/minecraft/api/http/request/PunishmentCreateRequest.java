package gg.modl.minecraft.api.http.request;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class PunishmentCreateRequest {
    String targetUuid, issuerName, issuerId, reason, severity, status;
    @SerializedName("type_ordinal") int typeOrdinal;
    Long duration;
    Map<String, Object> data;
    List<String> notes, attachedTicketIds;
}
