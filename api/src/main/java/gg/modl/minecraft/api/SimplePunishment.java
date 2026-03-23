package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

@Data @NoArgsConstructor @AllArgsConstructor
public class SimplePunishment {
    private static final String CATEGORY_BAN = "BAN", CATEGORY_MUTE = "MUTE", CATEGORY_KICK = "KICK";

    private @NotNull String type;
    private @Nullable String category;
    private @Nullable Long expiration;
    private @NotNull String description;
    private @NotNull String id;
    private @Nullable String issuerName;
    private @Nullable Long issuedAt;
    private @Nullable String playerDescription;
    private boolean started;
    private int ordinal;

    public boolean isBan() {
        if (category != null) return CATEGORY_BAN.equalsIgnoreCase(category);
        if (PunishmentTypeRegistry.isInitialized()) return PunishmentTypeRegistry.isBan(ordinal);
        if (ordinal >= PunishmentTypeRegistry.ORDINAL_BAN && ordinal <= PunishmentTypeRegistry.ORDINAL_BLACKLIST) return true;
        return CATEGORY_BAN.equalsIgnoreCase(type);
    }

    public boolean isMute() {
        if (category != null) return CATEGORY_MUTE.equalsIgnoreCase(category);
        if (PunishmentTypeRegistry.isInitialized()) return PunishmentTypeRegistry.isMute(ordinal);
        if (ordinal == PunishmentTypeRegistry.ORDINAL_MUTE) return true;
        return CATEGORY_MUTE.equalsIgnoreCase(type);
    }

    public boolean isKick() {
        return PunishmentTypeRegistry.isKick(ordinal) || CATEGORY_KICK.equalsIgnoreCase(type);
    }

    public boolean isPermanent() {
        return expiration == null;
    }

    public boolean isExpired() {
        return expiration != null && expiration < System.currentTimeMillis();
    }

    public Date getIssuedAsDate() {
        return issuedAt != null ? new Date(issuedAt) : null;
    }
}
