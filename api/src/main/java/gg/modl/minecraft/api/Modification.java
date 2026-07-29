package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@NoArgsConstructor @AllArgsConstructor @Getter
public final class Modification {
    private @SerializedName("type") Type type;
    private @SerializedName("issuerName") String issuer;
    private @SerializedName("date") Date issued;
    private @Nullable @SerializedName("effectiveDuration") Long effectiveDuration;

    public enum Type {
        MANUAL_PARDON,
        APPEAL_ACCEPT,
        SYSTEM_PARDON,
        MANUAL_DURATION_CHANGE,
        ROLLBACK,
        REMOVE,
        REVOKE,
    }
}
