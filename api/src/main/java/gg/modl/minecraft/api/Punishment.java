package gg.modl.minecraft.api;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Punishment {
    @SerializedName("id")
    private String id;

    @SerializedName("issuerName")
    private String issuerName;

    @SerializedName("issued")
    private Date issued;

    @SerializedName("started")
    private Date started;

    @SerializedName("type")
    private Type type;

    @SerializedName("typeOrdinal")
    private Integer typeOrdinal;

    @SerializedName("modifications")
    private List<Modification> modifications;

    @SerializedName("notes")
    private List<Note> notes;

    @SerializedName("attachedTicketIds")
    private List<String> attachedTicketIds;

    @SerializedName("data")
    private Map<String, Object> dataMap;

    // Lazy-initialized structured data
    private transient PunishmentData data;

    // Null-safe getters
    @NotNull
    public String getId() {
        return id != null ? id : "";
    }

    @NotNull
    public String getIssuerName() {
        return issuerName != null ? issuerName : "Unknown";
    }

    @NotNull
    public Date getIssued() {
        return issued != null ? issued : new Date(0);
    }

    @Nullable
    public Date getStarted() {
        return started;
    }

    @Nullable
    public Type getType() {
        return type;
    }

    /**
     * Get the punishment type ordinal.
     * This is more reliable than getType() for custom punishment types.
     */
    public int getTypeOrdinal() {
        if (typeOrdinal != null) {
            return typeOrdinal;
        }
        // Fallback to Type enum value if available
        return type != null ? type.getValue() : 0;
    }

    /**
     * Check if this punishment is a ban type using the PunishmentTypeRegistry.
     */
    public boolean isBanType() {
        return PunishmentTypeRegistry.isBan(getTypeOrdinal());
    }

    /**
     * Check if this punishment is a mute type using the PunishmentTypeRegistry.
     */
    public boolean isMuteType() {
        return PunishmentTypeRegistry.isMute(getTypeOrdinal());
    }

    /**
     * Check if this punishment is a kick type.
     */
    public boolean isKickType() {
        return PunishmentTypeRegistry.isKick(getTypeOrdinal());
    }

    /**
     * Get a display-friendly type category name.
     * First tries to get from dataMap (typeName), then falls back to registry detection.
     */
    public String getTypeCategory() {
        // First try to get the type name from the data map
        Object typeName = getDataMap().get("typeName");
        if (typeName instanceof String && !((String) typeName).isEmpty()) {
            return (String) typeName;
        }

        // Fall back to registry-based detection
        if (isBanType()) return "Ban";
        if (isMuteType()) return "Mute";
        if (isKickType()) return "Kick";
        return "Unknown";
    }

    @NotNull
    public List<Modification> getModifications() {
        return modifications != null ? modifications : Collections.emptyList();
    }

    @NotNull
    public List<Note> getNotes() {
        return notes != null ? notes : Collections.emptyList();
    }

    @NotNull
    public List<String> getAttachedTicketIds() {
        return attachedTicketIds != null ? attachedTicketIds : Collections.emptyList();
    }

    @NotNull
    public Map<String, Object> getDataMap() {
        return dataMap != null ? dataMap : Collections.emptyMap();
    }

    public PunishmentData getData() {
        if (data == null) {
            data = PunishmentData.fromMap(getDataMap());
        }
        return data;
    }

    public String getReason() {
        List<Note> noteList = getNotes();
        if (noteList.isEmpty()) {
            return "No reason provided";
        }
        return noteList.get(0).getText();
    }

    public Date getExpires() {
        Map<String, Object> map = getDataMap();
        Object expires = map.get("expires");
        if (expires instanceof Date) {
            return (Date) expires;
        } else if (expires instanceof Long) {
            return new Date((Long) expires);
        }
        return null;
    }

    public boolean isActive() {
        Map<String, Object> map = getDataMap();

        // Check manual active flag
        Object activeFlag = map.get("active");
        if (activeFlag instanceof Boolean && !((Boolean) activeFlag)) {
            return false;
        }

        // Check expiry date
        Date expiry = getExpires();
        if (expiry != null && expiry.before(new Date())) {
            return false;
        }

        // Bans and mutes must be started to be active
        if (isBanType() || isMuteType()) {
            return started != null;
        }

        return true;
    }

    @Getter
    public enum Type {
        KICK(0),
        MUTE(1),
        BAN(2),
        SECURITY_BAN(3),
        LINKED_BAN(4),
        BLACKLIST(5);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        public static Type fromValue(int value) {
            for (Type type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
        }
    }

    public Long getDuration() {
        Object duration = getDataMap().get("duration");
        if (duration instanceof Number) {
            return ((Number) duration).longValue();
        }
        return null;
    }

    public boolean isSilent() {
        Object silent = getDataMap().get("silent");
        return silent instanceof Boolean && (Boolean) silent;
    }
}
