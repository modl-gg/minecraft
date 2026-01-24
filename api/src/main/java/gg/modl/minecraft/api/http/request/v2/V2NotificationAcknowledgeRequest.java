package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * V2 API notification acknowledge request matching backend's AcknowledgeRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2NotificationAcknowledgeRequest {
    private String playerUuid;
    private List<String> notificationIds;
    private String acknowledgedAt;
}
