package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PardonPunishmentRequest {
    @NotNull
    private final String punishmentId;
    @NotNull
    private final String issuerName;
    @Nullable
    private final String reason;
    @Nullable
    private final String expectedType; // "ban", "mute", or null for no type checking
}