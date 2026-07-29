package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class CreatePlayerNoteRequest {
    transient @NotNull String targetUuid;
    @Nullable String issuerName, issuerId;
    @NotNull String text;
}
