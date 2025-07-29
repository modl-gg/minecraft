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
    private String type; // "BAN", "MUTE", or "KICK"
    
    private boolean started;
    
    @Nullable
    private Long expiration; // Unix timestamp in milliseconds
    
    @NotNull
    private String description;
    
    @NotNull
    private String id;
    
    private int ordinal; // The punishment type ordinal (0=kick, 1=mute, 2+=ban)
    
    /**
     * Check if this is a ban punishment
     */
    public boolean isBan() {
        return "BAN".equalsIgnoreCase(type);
    }
    
    /**
     * Check if this is a mute punishment
     */
    public boolean isMute() {
        return "MUTE".equalsIgnoreCase(type);
    }
    
    /**
     * Check if this is a kick punishment
     */
    public boolean isKick() {
        return "KICK".equalsIgnoreCase(type);
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

}