package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PardonPunishmentRequest {
    private transient @NotNull final String punishmentId;
    private @NotNull final String issuerName;
    private @Nullable final String reason, expectedType;
}