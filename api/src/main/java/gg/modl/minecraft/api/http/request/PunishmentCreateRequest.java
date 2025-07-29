package gg.modl.minecraft.api.http.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PunishmentCreateRequest {
    private final String targetUuid;
    private final String issuerName;
    private final Integer type_ordinal;
    private final String reason;
    private final Long duration;
    private final Map<String, Object> data;
    private final List<String> notes;
    private final List<String> attachedTicketIds;
    private final String severity;
    private final String status;
}