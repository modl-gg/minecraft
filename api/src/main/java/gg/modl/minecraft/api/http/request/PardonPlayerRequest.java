package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PardonPlayerRequest {
    private @NotNull final String playerName;
    private @NotNull final String issuerName;
    private @Nullable final String punishmentType;
    private @Nullable final String reason;
}