package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simplified punishment object for API responses with only essential fields
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimplePunishment {
    @NotNull
    private String type; // Punishment type name (e.g., "Manual Ban", "Chat Offense")

    @Nullable
    private String category; // Category: "BAN", "MUTE", or "OTHER"

    private boolean started;

    @Nullable
    private Long expiration; // Unix timestamp in milliseconds

    @NotNull
    private String description;

    @NotNull
    private String id;

    private int ordinal; // The punishment type ordinal (0=kick, 1=mute, 2+=ban)

    @Nullable
    private String issuerName; // Name of the staff who issued the punishment

    @Nullable
    private Long issuedAt; // Unix timestamp in milliseconds when punishment was issued

    @Nullable
    private String playerDescription; // Player-facing description from punishment type config

    /**
     * Check if this is a ban punishment.
     * Backend-provided category is most authoritative since it's determined per-punishment
     * based on the specific severity/offense level, while the registry is per-type across all levels.
     */
    public boolean isBan() {
        // Backend-provided category is most authoritative (per-punishment, not per-type)
        if (category != null) {
            return "BAN".equalsIgnoreCase(category);
        }
        // Check registry (per-type level, may be inaccurate for types with mixed ban/mute durations)
        if (PunishmentTypeRegistry.isInitialized()) {
            return PunishmentTypeRegistry.isBan(ordinal);
        }
        // Fallback to ordinal for legacy support (ordinals 2-5 are admin ban types)
        if (ordinal >= 2 && ordinal <= 5) {
            return true;
        }
        // Legacy fallback to type string
        return "BAN".equalsIgnoreCase(type);
    }

    /**
     * Check if this is a mute punishment.
     * Backend-provided category is most authoritative since it's determined per-punishment
     * based on the specific severity/offense level, while the registry is per-type across all levels.
     */
    public boolean isMute() {
        // Backend-provided category is most authoritative (per-punishment, not per-type)
        if (category != null) {
            return "MUTE".equalsIgnoreCase(category);
        }
        // Check registry (per-type level, may be inaccurate for types with mixed ban/mute durations)
        if (PunishmentTypeRegistry.isInitialized()) {
            return PunishmentTypeRegistry.isMute(ordinal);
        }
        // Fallback to ordinal for legacy support (ordinal 1 is admin mute type)
        if (ordinal == 1) {
            return true;
        }
        // Legacy fallback to type string
        return "MUTE".equalsIgnoreCase(type);
    }

    /**
     * Check if this is a kick punishment.
     * Uses ordinal matching (ordinal 0 is kick type).
     */
    public boolean isKick() {
        return PunishmentTypeRegistry.isKick(ordinal) || "KICK".equalsIgnoreCase(type);
    }
    
    /**
     * Check if the punishment is permanent (no expiration)
     */
    public boolean isPermanent() {
        return expiration == null;
    }
    
    /**
     * Check if the punishment has expired
     */
    public boolean isExpired() {
        return expiration != null && expiration < System.currentTimeMillis();
    }
    
    /**
     * Get expiration as Date object (for backward compatibility)
     */
    public java.util.Date getExpirationAsDate() {
        return expiration != null ? new java.util.Date(expiration) : null;
    }

    /**
     * Get issued timestamp as Date object
     */
    public java.util.Date getIssuedAsDate() {
        return issuedAt != null ? new java.util.Date(issuedAt) : null;
    }

}