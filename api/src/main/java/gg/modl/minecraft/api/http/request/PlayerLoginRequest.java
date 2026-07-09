package gg.modl.minecraft.api.http.request;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Data
public class PlayerLoginRequest {
    @SerializedName("minecraftUUID")
    private @NotNull final String minecraftUuid;
    private @NotNull final String username;
    @SerializedName("ip")
    private @Nullable final String ipAddress;
    private @Nullable final String skinHash, serverName;
    private @Nullable final Map<String, Object> ipInfo;
    private @Nullable String serverInstanceId;
}
