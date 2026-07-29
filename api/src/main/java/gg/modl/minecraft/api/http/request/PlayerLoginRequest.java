package gg.modl.minecraft.api.http.request;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Value
public class PlayerLoginRequest {
    @SerializedName("minecraftUUID")
    @NotNull String minecraftUuid;
    @NotNull String username;
    @SerializedName("ip")
    @Nullable String ipAddress;
    @Nullable String skinHash, serverName;
    @Nullable Map<String, Object> ipInfo;
    @Nullable String serverInstanceId;
}
