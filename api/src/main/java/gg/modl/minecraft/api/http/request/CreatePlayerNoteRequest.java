package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class CreatePlayerNoteRequest {
    private @NotNull final String targetUuid;
    private @NotNull final String issuerName;
    private @NotNull final String text;
}