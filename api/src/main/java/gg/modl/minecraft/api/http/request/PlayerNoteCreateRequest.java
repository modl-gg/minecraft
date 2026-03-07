package gg.modl.minecraft.api.http.request;

import lombok.Data;

@Data
public class PlayerNoteCreateRequest {
    private transient final String targetUuid;
    private final String issuerName, issuerId, text;
}