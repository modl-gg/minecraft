package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class CreatePlayerNoteRequest {
    private transient @NotNull final String targetUuid;
    private @Nullable final String issuerName, issuerId;
    private @NotNull final String text;
}