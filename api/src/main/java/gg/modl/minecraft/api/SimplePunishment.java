package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simplified punishment for API responses with only essential fields.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class SimplePunishment {
    private static final String CATEGORY_BAN = "BAN";
    private static final String CATEGORY_MUTE = "MUTE";
    private static final String CATEGORY_KICK = "KICK";

    private @NotNull String type;

    private @Nullable String category;

    private boolean started;

    private @Nullable Long expiration;

    private @NotNull String description;

    private @NotNull String id;

    private int ordinal;

    private @Nullable String issuerName;

    private @Nullable Long issuedAt;

    private @Nullable String playerDescription;

    /**
     * Backend-provided category is most authoritative (per-punishment, not per-type),
     * since the registry is per-type across all severity levels.
     */
    public boolean isBan() {
        if (category != null) return CATEGORY_BAN.equalsIgnoreCase(category);
        if (PunishmentTypeRegistry.isInitialized()) return PunishmentTypeRegistry.isBan(ordinal);
        if (ordinal >= PunishmentTypeRegistry.ORDINAL_BAN && ordinal <= PunishmentTypeRegistry.ORDINAL_BLACKLIST) return true;
        return CATEGORY_BAN.equalsIgnoreCase(type);
    }

    /**
     * Backend-provided category is most authoritative (per-punishment, not per-type),
     * since the registry is per-type across all severity levels.
     */
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

    public java.util.Date getIssuedAsDate() {
        return issuedAt != null ? new java.util.Date(issuedAt) : null;
    }
}
