package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@NoArgsConstructor @AllArgsConstructor @Getter
public final class Modification {
    private @SerializedName("type") Type type;
    private @SerializedName("issuerName") String issuer;
    private @SerializedName("date") Date issued;
    private @SerializedName("effectiveDuration") Long effectiveDuration;

    /**
     * Returns null for permanent punishments.
     */
    public @Nullable Long getEffectiveDuration() {
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
