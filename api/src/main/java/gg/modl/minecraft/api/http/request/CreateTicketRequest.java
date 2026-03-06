package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
public class CreateTicketRequest {
    private @NotNull final String creatorUuid;
    private @Nullable final String creatorName;
    private @NotNull final String type;
    private @Nullable final String subject;
    private @Nullable final String description;
    private @Nullable final String reportedPlayerUuid;
    private @Nullable final String reportedPlayerName;
    private @Nullable final List<String> chatMessages;
    private @Nullable final List<String> tags;
    private @Nullable final String priority;
    private @Nullable final String createdServer;
}