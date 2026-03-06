package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@Getter @NoArgsConstructor @AllArgsConstructor
public class Note {
    private @SerializedName("text") String text;
    private @SerializedName("date") Date date;
    private @SerializedName("issuerName") String issuerName;
    private @SerializedName("issuerId") String issuerId;
}
