package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
public class CreateTicketRequest {
    @NotNull
    private final String creatorUuid;
    @Nullable
    private final String creatorName;
    @NotNull
    private final String type;
    @Nullable
    private final String subject;
    @Nullable
    private final String description;
    @Nullable
    private final String reportedPlayerUuid;
    @Nullable
    private final String reportedPlayerName;
    @Nullable
    private final List<String> chatMessages;
    @Nullable
    private final List<String> tags;
    @Nullable
    private final String priority;
    @Nullable
    private final String createdServer;
}