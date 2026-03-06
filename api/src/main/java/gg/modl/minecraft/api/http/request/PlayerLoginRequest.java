package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PlayerLoginRequest {
    private @NotNull final String minecraftUuid;
    private @NotNull final String username;
    private @Nullable final String ipAddress;
    private @Nullable final String skinHash;
    private @Nullable final JsonObject ipInfo;
    private @Nullable final String serverName;
}