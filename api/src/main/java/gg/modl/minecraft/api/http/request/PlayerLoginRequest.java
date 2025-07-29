package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PlayerLoginRequest {
    @NotNull
    private final String minecraftUuid;
    @NotNull
    private final String username;
    @Nullable
    private final String ipAddress;
    @Nullable
    private final String skinHash;
    @Nullable
    private final JsonObject ipInfo;
    @Nullable
    private final String serverName;
}