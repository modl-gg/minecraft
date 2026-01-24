package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API punishment acknowledge request matching backend's AcknowledgeRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2PunishmentAcknowledgeRequest {
    private String punishmentId;
    private String playerUuid;
    private String executedAt;
    private boolean success;
    private String errorMessage;
}
