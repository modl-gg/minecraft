package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class StartupRequest {
    private String pluginVersion;
    private String platformType;
    private String serverVersion;
    private int maxPlayers;
}
