package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
public class CreatePunishmentRequest {
    @NotNull
    private final String targetUuid;
    @NotNull
    private final String issuerName;
    @SerializedName("typeOrdinal")
    private final int typeOrdinal;
    @Nullable
    private final String reason;
    private final long duration;
    @Nullable
    private final JsonObject data;
    @Nullable
    private final List<String> notes;
    @Nullable
    private final List<String> attachedTicketIds;
}