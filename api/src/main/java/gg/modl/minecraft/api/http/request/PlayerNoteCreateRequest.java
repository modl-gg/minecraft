package gg.modl.minecraft.api.http.request;

import lombok.Data;

@Data
public class PlayerNoteCreateRequest {
    private final String targetUuid;
    private final String issuerName;
    private final String text;
}