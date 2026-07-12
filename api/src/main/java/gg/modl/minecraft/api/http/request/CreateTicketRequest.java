package gg.modl.minecraft.api.http.request;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value @Builder
public class CreateTicketRequest {
    @NotNull String creatorUuid, type;
    @Nullable String creatorName, subject, description, reportedPlayerUuid, reportedPlayerName, priority, createdServer;
    @Nullable List<String> chatMessages, tags;
    @Nullable String replayUrl;
}
