package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class PlayerDisconnectRequest {
    @NotNull String minecraftUuid;
    long sessionDurationMs;
    @Nullable String serverInstanceId;
}
