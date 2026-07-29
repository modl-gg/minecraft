package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class StartupRequest {
    String pluginVersion;
    String platformType;
    String serverVersion;
    int maxPlayers;
}
