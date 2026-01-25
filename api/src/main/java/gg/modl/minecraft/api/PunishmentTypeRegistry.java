package gg.modl.minecraft.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for punishment type information.
 * Populated from the server's punishment types configuration.
 * Used by SimplePunishment to determine if a punishment is a ban, mute, or kick.
 */
public class PunishmentTypeRegistry {

    private static final Map<Integer, PunishmentTypeInfo> registry = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    /**
     * Register a punishment type with its ordinal and type classification.
     *
     * @param ordinal The punishment type ordinal
     * @param isBan Whether this type is a ban (blocks login)
     * @param isMute Whether this type is a mute (blocks chat)
     */
    public static void register(int ordinal, boolean isBan, boolean isMute) {
        registry.put(ordinal, new PunishmentTypeInfo(isBan, isMute));
        initialized = true;
    }

    /**
     * Register a punishment type from its duration type string.
     *
     * @param ordinal The punishment type ordinal
     * @param durationType The duration type (e.g., "ban", "mute", "permanent ban", "permanent mute")
     */
    public static void registerFromDurationType(int ordinal, String durationType) {
        if (durationType == null) {
            // No duration type - check if it's a known administrative type
            registerAdministrativeType(ordinal);
            return;
        }

        String lower = durationType.toLowerCase();
        boolean isBan = lower.contains("ban");
        boolean isMute = lower.contains("mute");
        register(ordinal, isBan, isMute);
    }

    /**
     * Register administrative punishment types with known ordinals.
     * These are the built-in types that don't have duration configurations.
     */
    private static void registerAdministrativeType(int ordinal) {
        switch (ordinal) {
            case 0: // Kick
                register(ordinal, false, false);
                break;
            case 1: // Manual Mute
                register(ordinal, false, true);
                break;
            case 2: // Manual Ban
            case 3: // Security Ban
            case 4: // Linked Ban
            case 5: // Blacklist
                register(ordinal, true, false);
                break;
            default:
                // Unknown type - don't register
                break;
        }
    }

    /**
     * Register all administrative types (ordinals 0-5).
     */
    public static void registerAdministrativeTypes() {
        register(0, false, false); // Kick
        register(1, false, true);  // Manual Mute
        register(2, true, false);  // Manual Ban
        register(3, true, false);  // Security Ban
        register(4, true, false);  // Linked Ban
        register(5, true, false);  // Blacklist
        initialized = true;
    }

    /**
     * Check if a punishment with the given ordinal is a ban.
     *
     * @param ordinal The punishment type ordinal
     * @return true if this is a ban type, false otherwise
     */
    public static boolean isBan(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) {
            return info.isBan;
        }
        // Fallback for unregistered types - ordinals 2-5 are administrative bans
        return ordinal >= 2 && ordinal <= 5;
    }

    /**
     * Check if a punishment with the given ordinal is a mute.
     *
     * @param ordinal The punishment type ordinal
     * @return true if this is a mute type, false otherwise
     */
    public static boolean isMute(int ordinal) {
        PunishmentTypeInfo info = registry.get(ordinal);
        if (info != null) {
            return info.isMute;
        }
        // Fallback for unregistered types - ordinal 1 is administrative mute
        return ordinal == 1;
    }

    /**
     * Check if a punishment with the given ordinal is a kick.
     *
     * @param ordinal The punishment type ordinal
     * @return true if this is a kick type
     */
    public static boolean isKick(int ordinal) {
        return ordinal == 0;
    }

    /**
     * Check if the registry has been initialized with punishment types.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Clear all registered punishment types.
     */
    public static void clear() {
        registry.clear();
        initialized = false;
    }

    /**
     * Get the number of registered punishment types.
     */
    public static int size() {
        return registry.size();
    }

    private static class PunishmentTypeInfo {
        final boolean isBan;
        final boolean isMute;

        PunishmentTypeInfo(boolean isBan, boolean isMute) {
            this.isBan = isBan;
            this.isMute = isMute;
        }
    }
}
