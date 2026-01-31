package gg.modl.minecraft.core.util;

import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.providers.ComponentConverterProvider;

/**
 * Utility class for menu-related operations.
 */
public final class MenuUtil {

    private static Boolean protocolizeAvailable = null;
    private static Boolean isBungeeCord = null;

    private MenuUtil() {}

    /**
     * Check if we're running on BungeeCord.
     */
    private static boolean isBungeeCord() {
        if (isBungeeCord == null) {
            try {
                Class.forName("net.md_5.bungee.api.ProxyServer");
                isBungeeCord = true;
            } catch (ClassNotFoundException e) {
                isBungeeCord = false;
            }
        }
        return isBungeeCord;
    }

    /**
     * Check if Protocolize is properly initialized.
     * Returns true on non-BungeeCord platforms (they don't need Protocolize for menus).
     * On BungeeCord, returns false if Protocolize failed to load (e.g., due to FastUtil incompatibility).
     * Result is cached after first check.
     */
    public static boolean isProtocolizeAvailable() {
        // Only BungeeCord requires Protocolize for menus
        if (!isBungeeCord()) {
            return true;
        }

        if (protocolizeAvailable == null) {
            try {
                protocolizeAvailable = Protocolize.getService(ComponentConverterProvider.class) != null;
            } catch (Exception | NoClassDefFoundError e) {
                protocolizeAvailable = false;
            }
        }
        return protocolizeAvailable;
    }

    /**
     * Get an error message to show when menus are unavailable.
     */
    public static String getMenuUnavailableMessage() {
        return "\u00a7cDue to a breaking change made by BungeeCord in commit \u00a7e#5dad410\u00a7c on May 30, 2025, menus cannot be supported on BungeeCord until Protocolize is updated. To enable menus, either downgrade to BungeeCord build \u00a7e#1957\u00a7c or install Velocity (highly recommended, as it is superior to BungeeCord in every possible way).";
    }
}
