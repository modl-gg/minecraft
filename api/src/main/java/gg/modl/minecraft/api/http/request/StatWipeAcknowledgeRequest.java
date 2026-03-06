package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data @NoArgsConstructor @AllArgsConstructor
public class StatWipeAcknowledgeRequest {
    private @NotNull String punishmentId;
    private @Nullable String serverName;
    private boolean success;
}
