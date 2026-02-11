package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * V2 API login request matching backend's LoginRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2LoginRequest {
    private String minecraftUUID;
    private String username;
    private String ip;
    private Map<String, Object> ipInfo;
    private String skinHash;
    private String serverName;
}
