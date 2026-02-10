package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class PlayerDisconnectRequest {
    @NotNull
    private final String minecraftUuid;
    private final long sessionDurationMs;
}