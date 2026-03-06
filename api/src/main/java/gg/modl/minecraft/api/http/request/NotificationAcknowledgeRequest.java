package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class NotificationAcknowledgeRequest {
    private @NotNull String playerUuid;
    
    private @NotNull List<String> notificationIds;
    
    private @NotNull String acknowledgedAt;
}
