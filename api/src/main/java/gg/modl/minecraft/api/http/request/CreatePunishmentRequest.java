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
    private @Nullable final String issuerName, issuerId;
    private @Nullable final String reason;
    private @Nullable final JsonObject data;
    private @Nullable final List<String> notes, attachedTicketIds;
    private @SerializedName("type_ordinal") final int typeOrdinal;
    private final long duration;
}