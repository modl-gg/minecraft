package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public final class Modification {
    @SerializedName("type")
    private Type type;

    @SerializedName("issuerName")
    private String issuer;

    @SerializedName("date")
    private Date issued;

    @SerializedName("effectiveDuration")
    private Long effectiveDuration;

    @Nullable
    public Type getType() {
        return type;
    }

    @NotNull
    public String getIssuer() {
        return issuer != null ? issuer : "Unknown";
    }

    @NotNull
    public Date getIssued() {
        return issued != null ? issued : new Date(0);
    }

    /**
     * Get the effective duration in milliseconds.
     * Returns null for permanent punishments.
     */
    @Nullable
    public Long getEffectiveDuration() {
        return effectiveDuration;
    }

    public enum Type {
        MANUAL_DURATION_CHANGE,
        MANUAL_PARDON,
        SYSTEM_PARDON,
        APPEAL_REJECT,
        APPEAL_DURATION_CHANGE,
        APPEAL_ACCEPT,
        SET_ALT_BLOCKING_TRUE,
        SET_WIPING_TRUE,
        SET_ALT_BLOCKING_FALSE,
        SET_WIPING_FALSE,
    }
}
