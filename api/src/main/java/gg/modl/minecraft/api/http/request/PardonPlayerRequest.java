package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PardonPlayerRequest {
    @NotNull private final String playerName;
    @NotNull private final String issuerName;
    @Nullable private final String punishmentType;
    @Nullable private final String reason;
}