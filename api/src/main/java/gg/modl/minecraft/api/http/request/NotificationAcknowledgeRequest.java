package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationAcknowledgeRequest {
    @NotNull
    private String playerUuid;
    
    @NotNull
    private List<String> notificationIds;
    
    @NotNull
    private String acknowledgedAt;
}