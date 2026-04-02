package gg.modl.minecraft.core.util;

import gg.modl.minecraft.api.PunishmentTypeRegistry;
import gg.modl.proto.modl.v1.SimplePunishment;

import java.util.Date;

public final class PunishmentHelper {

    private static final String CATEGORY_BAN = "BAN";
    private static final String CATEGORY_MUTE = "MUTE";
    private static final String CATEGORY_KICK = "KICK";

    private final SimplePunishment punishment;

    public PunishmentHelper(SimplePunishment punishment) {
        this.punishment = punishment;
    }

    public SimplePunishment proto() {
        return punishment;
    }

    public boolean isBan() {
        if (punishment.hasCategory()) {
            return CATEGORY_BAN.equalsIgnoreCase(punishment.getCategory());
        }
        if (PunishmentTypeRegistry.isInitialized()) {
            return PunishmentTypeRegistry.isBan(punishment.getOrdinal());
        }
        return CATEGORY_BAN.equalsIgnoreCase(punishment.getType());
    }

    public boolean isMute() {
        if (punishment.hasCategory()) {
            return CATEGORY_MUTE.equalsIgnoreCase(punishment.getCategory());
        }
        if (PunishmentTypeRegistry.isInitialized()) {
            return PunishmentTypeRegistry.isMute(punishment.getOrdinal());
        }
        return CATEGORY_MUTE.equalsIgnoreCase(punishment.getType());
    }

    public boolean isKick() {
        return PunishmentTypeRegistry.isKick(punishment.getOrdinal())
                || CATEGORY_KICK.equalsIgnoreCase(punishment.getType());
    }

    public boolean isPermanent() {
        return !punishment.hasExpiration();
    }

    public boolean isExpired() {
        return punishment.hasExpiration() && punishment.getExpiration() < System.currentTimeMillis();
    }

    public Date getIssuedAsDate() {
        return punishment.hasIssuedAt() ? new Date(punishment.getIssuedAt()) : null;
    }
}
