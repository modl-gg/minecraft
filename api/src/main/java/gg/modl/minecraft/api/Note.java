package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Note {
    @SerializedName("text")
    private String text;

    @SerializedName("date")
    private Date date;

    @SerializedName("issuerName")
    private String issuerName;

    @SerializedName("issuerId")
    private String issuerId;

    @NotNull
    public String getText() {
        return text != null ? text : "";
    }

    @NotNull
    public Date getDate() {
        return date != null ? date : new Date(0);
    }

    @NotNull
    public String getIssuerName() {
        return issuerName != null ? issuerName : "Unknown";
    }

    @NotNull
    public String getIssuerId() {
        return issuerId != null ? issuerId : "";
    }
}
