package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class PardonPlayerRequest {
    @NotNull String playerName;
    @Nullable String issuerName, issuerId;
    @Nullable String punishmentType, reason;
}
