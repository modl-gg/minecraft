package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@AllArgsConstructor
public class CreateTicketRequest {
    private @NotNull final String creatorUuid, type;
    private @Nullable final String creatorName, subject, description, reportedPlayerUuid, reportedPlayerName, priority, createdServer;
    private @Nullable final List<String> chatMessages, tags;
    private @Nullable final String replayUrl;

    /**
     * Backwards-compatible constructor without replayUrl.
     */
    public CreateTicketRequest(@NotNull String creatorUuid, @NotNull String type,
                               @Nullable String creatorName, @Nullable String subject,
                               @Nullable String description, @Nullable String reportedPlayerUuid,
                               @Nullable String reportedPlayerName, @Nullable String priority,
                               @Nullable String createdServer, @Nullable List<String> chatMessages,
                               @Nullable List<String> tags) {
        this(creatorUuid, type, creatorName, subject, description, reportedPlayerUuid,
             reportedPlayerName, priority, createdServer, chatMessages, tags, null);
    }
}