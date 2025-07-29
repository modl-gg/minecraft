package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class CreatePlayerNoteRequest {
    @NotNull
    private final String targetUuid;
    @NotNull
    private final String issuerName;
    @NotNull
    private final String text;
}