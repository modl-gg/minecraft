package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API pardon player request matching backend's PardonPlayerRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2PardonPlayerRequest {
    private String playerName;
    private String issuerName;
    private String punishmentType;
    private String reason;
}
