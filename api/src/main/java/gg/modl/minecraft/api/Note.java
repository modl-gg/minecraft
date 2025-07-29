package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@Getter
@RequiredArgsConstructor
public class Note {
    @NotNull
    @SerializedName("text")
    private final String text;

    @NotNull
    @SerializedName("date")
    private final Date date;

    @NotNull
    @SerializedName("issuerName")
    private final String issuerName;

    @NotNull
    @SerializedName("issuerId")
    private final String issuerId;
}
