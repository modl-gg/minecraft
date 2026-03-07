package gg.modl.minecraft.api.http.request;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
public class CreateTicketRequest {
    private @NotNull final String creatorUuid, type;
    private @Nullable final String creatorName, subject, description, reportedPlayerUuid, reportedPlayerName, priority, createdServer;
    private @Nullable final List<String> chatMessages, tags;
}