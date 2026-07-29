package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class PardonPunishmentRequest {
    transient @NotNull String punishmentId;
    @Nullable String issuerName, issuerId;
    @Nullable String reason, expectedType;
}
