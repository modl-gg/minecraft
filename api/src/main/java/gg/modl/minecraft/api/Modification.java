package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@RequiredArgsConstructor
@Getter
public final class Modification {
    @NotNull
    @SerializedName("type")
    private final Type type;
    @NotNull
    @SerializedName("issuerName")
    private final String issuer;
    @NotNull
    @SerializedName("issued")
    private final Date issued;
    @SerializedName("effectiveDuration")
    private final long effectiveDuration;

    @RequiredArgsConstructor
    public enum Type {
        MANUAL_DURATION_CHANGE,
        MANUAL_PARDON,
        APPEAL_REJECT,
        APPEAL_DURATION_CHANGE,
        APPEAL_ACCEPT,
        SET_ALT_BLOCKING_TRUE,
        SET_WIPING_TRUE,
        SET_ALT_BLOCKING_FALSE,
        SET_WIPING_FALSE,
    }
}
