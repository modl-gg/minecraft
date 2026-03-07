package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PardonPunishmentRequest {
    private transient @NotNull final String punishmentId;
    private @Nullable final String issuerName, issuerId;
    private @Nullable final String reason, expectedType;
}