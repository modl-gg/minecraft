package gg.modl.minecraft.api.http.request;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value
public class CreatePunishmentRequest {
    @NotNull String targetUuid;
    @Nullable String issuerName, issuerId;
    @Nullable String reason;
    @Nullable JsonObject data;
    @Nullable List<String> notes, attachedTicketIds;
    @SerializedName("type_ordinal") int typeOrdinal;
    long duration;
}
