package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data @NoArgsConstructor @AllArgsConstructor
public class PunishmentAcknowledgeRequest {
    private @NotNull String punishmentId;
    private @NotNull String playerUuid;
    private @NotNull String executedAt;
    private boolean success;
    private @Nullable String errorMessage;
}
