package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class PunishmentAcknowledgeRequest {
    @NotNull String punishmentId, playerUuid, executedAt;
    @Nullable String errorMessage;
    boolean success;
}
