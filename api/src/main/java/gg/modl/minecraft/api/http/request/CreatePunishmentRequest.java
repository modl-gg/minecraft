package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
public class CreatePunishmentRequest {
    private @NotNull final String targetUuid;
    private @NotNull final String issuerName;
    private @SerializedName("type_ordinal") final int typeOrdinal;
    private @Nullable final String reason;
    private final long duration;
    private @Nullable final JsonObject data;
    private @Nullable final List<String> notes;
    private @Nullable final List<String> attachedTicketIds;
}