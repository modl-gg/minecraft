package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API login request matching backend's LoginRequest record.
 * Only includes the fields the backend expects.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2LoginRequest {
    private String minecraftUUID;
    private String username;
    private String ip;
}
