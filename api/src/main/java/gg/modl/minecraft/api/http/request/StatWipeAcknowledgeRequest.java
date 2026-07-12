package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class StatWipeAcknowledgeRequest {
    @NotNull String punishmentId;
    @Nullable String serverName;
    boolean success;
}
