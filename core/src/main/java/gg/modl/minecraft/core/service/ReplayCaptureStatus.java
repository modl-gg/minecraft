package gg.modl.minecraft.core.service;

public enum ReplayCaptureStatus {
    OK,
    FABRIC_DISABLED,
    NO_BRIDGE_CONNECTED,
    NO_ACTIVE_RECORDING,
    NOT_LOCAL,
    ERROR;

    public static ReplayCaptureStatus fromWire(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return ReplayCaptureStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ERROR;
        }
    }
}
