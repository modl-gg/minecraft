package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API disconnect request matching backend's DisconnectRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2DisconnectRequest {
    private String minecraftUuid;
    private long sessionDurationMs;
}
