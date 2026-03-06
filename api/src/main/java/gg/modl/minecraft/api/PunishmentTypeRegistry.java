package gg.modl.minecraft.api;

import lombok.Getter;
import lombok.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for punishment type classification (ban/mute/kick).
 * Populated from the server's punishment types configuration.
 */
public class PunishmentTypeRegistry {
    public static final int ORDINAL_KICK = 0;
    public static final int ORDINAL_MUTE = 1;
    public static final int ORDINAL_BAN = 2;
    public static final int ORDINAL_SECURITY_BAN = 3;
    public static final int ORDINAL_LINKED_BAN = 4;
    public static final int ORDINAL_BLACKLIST = 5;

    private static final Map<Integer, PunishmentTypeInfo> registry = new ConcurrentHashMap<>();
    @Getter private static volatile boolean initialized = false;

    public static void register(int ordinal, boolean isBan, boolean isMute) {
        registry.put(ordinal, new PunishmentTypeInfo(isBan, isMute));
        initialized = true;
    }

    public static void registerAdministrativeTypes() {
        register(ORDINAL_KICK, false, false);
        register(ORDINAL_MUTE, false, true);
        register(ORDINAL_BAN, true, false);
        register(ORDINAL_SECURITY_BAN, true, false);
        register(ORDINAL_LINKED_BAN, true, false);
        register(ORDINAL_BLACKLIST, true, false);
        initialized = true;
    }

    public static boolean isBan(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) return info.isBan();
        return ordinal >= ORDINAL_BAN && ordinal <= ORDINAL_BLACKLIST;
    }

    public static boolean isMute(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) return info.isMute();
        return ordinal == ORDINAL_MUTE;
    }

    public static boolean isKick(int ordinal) {
        return ordinal == ORDINAL_KICK;
    }

    @Value
    private static class PunishmentTypeInfo {
        boolean isBan;
        boolean isMute;
    }
}
