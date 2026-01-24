package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API pardon punishment request matching backend's PardonRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2PardonPunishmentRequest {
    private String issuerName;
    private String reason;
    private String expectedType;
}
