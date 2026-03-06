package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@Getter @NoArgsConstructor @AllArgsConstructor
public class Note {
    private static final String UNKNOWN_ISSUER = "Unknown";

    private @SerializedName("text") String text;

    private @SerializedName("date") Date date;

    private @SerializedName("issuerName") String issuerName;

    private @SerializedName("issuerId") String issuerId;

    public @NotNull String getText() {
        return text != null ? text : "";
    }

    public @NotNull Date getDate() {
        return date != null ? date : new Date(0);
    }

    public @NotNull String getIssuerName() {
        return issuerName != null ? issuerName : UNKNOWN_ISSUER;
    }

    public @NotNull String getIssuerId() {
        return issuerId != null ? issuerId : "";
    }
}
