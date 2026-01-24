package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class PlayerLoginRequest {
    @NotNull
    @SerializedName("minecraftUUID")
    private final String minecraftUuid;
    @NotNull
    private final String username;
    @Nullable
    @SerializedName("ip")
    private final String ipAddress;
    @Nullable
    private final String skinHash;
    @Nullable
    private final JsonObject ipInfo;
    @Nullable
    private final String serverName;
}