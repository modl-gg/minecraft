package gg.modl.minecraft.bridge.reporter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TicketRequest {
    String creatorUuid;
    String creatorName;
    String type;
    String subject;
    String description;
    String reportedPlayerUuid;
    String reportedPlayerName;
    String tagsJoined;
    String priority;
    String createdServer;
    String replayUrl;
}
