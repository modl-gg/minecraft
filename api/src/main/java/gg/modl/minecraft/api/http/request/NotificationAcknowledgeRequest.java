package gg.modl.minecraft.api.http.request;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Value
public class NotificationAcknowledgeRequest {
    @NotNull String playerUuid, acknowledgedAt;
    @NotNull List<String> notificationIds;
}
