package gg.modl.minecraft.core.util;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.ApiVersion;
import gg.modl.minecraft.core.HttpClientHolder;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.api.http.response.SyncResponse;

import java.util.List;
import java.util.UUID;

/**
 * Utility class for checking permissions based on cached staff data from the panel
 */
public class PermissionUtil {

    private static HttpClientHolder httpClientHolder;

    public static void setHttpClientHolder(HttpClientHolder holder) {
        httpClientHolder = holder;
    }

    private static boolean isV1Api() {
        return httpClientHolder != null && httpClientHolder.getApiVersion() == ApiVersion.V1;
    }

    /**
     * Check if a command issuer has the required permission
     * @param issuer The command issuer (player or console)
     * @param cache The cache containing staff permissions
     * @param permission The permission to check
     * @return true if the issuer has permission, false otherwise
     */
    public static boolean hasPermission(CommandIssuer issuer, Cache cache, String permission) {
        // Console always has permission
        if (!issuer.isPlayer()) {
            return true;
        }

        UUID playerUuid = issuer.getUniqueId();

        // Check panel-based permission cache
        if (cache.hasPermission(playerUuid, permission)) {
            return true;
        }

        // V1 API fallback: platform permission node grants all permissions
        return isV1Api() && issuer.hasPermission("modl.staff");
    }

    /**
     * Check if a command issuer has any of the required permissions
     * @param issuer The command issuer (player or console)
     * @param cache The cache containing staff permissions
     * @param permissions List of permissions to check (OR logic)
     * @return true if the issuer has any of the permissions, false otherwise
     */
    public static boolean hasAnyPermission(CommandIssuer issuer, Cache cache, String... permissions) {
        // Console always has permission
        if (!issuer.isPlayer()) {
            return true;
        }
        
        for (String permission : permissions) {
            if (hasPermission(issuer, cache, permission)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get the staff member data for a command issuer
     * @param issuer The command issuer
     * @param cache The cache containing staff data
     * @return The staff member data, or null if not staff
     */
    public static SyncResponse.ActiveStaffMember getStaffMember(CommandIssuer issuer, Cache cache) {
        if (!issuer.isPlayer()) {
            return null;
        }
        
        return cache.getStaffMember(issuer.getUniqueId());
    }
    
    /**
     * Check if a command issuer is a staff member
     * @param issuer The command issuer
     * @param cache The cache containing staff data
     * @return true if the issuer is staff, false otherwise
     */
    public static boolean isStaff(CommandIssuer issuer, Cache cache) {
        if (!issuer.isPlayer()) {
            return true; // Console is considered staff
        }

        // Check panel-based staff caches first
        if (cache.isStaffMemberByPermissions(issuer.getUniqueId()) || cache.isStaffMember(issuer.getUniqueId())) {
            return true;
        }

        // V1 API fallback: platform permission node
        return isV1Api() && issuer.hasPermission("modl.staff");
    }
    
    /**
     * Get all permissions for a command issuer
     * @param issuer The command issuer
     * @param cache The cache containing staff data
     * @return List of permissions, or empty list if not staff
     */
    public static List<String> getPermissions(CommandIssuer issuer, Cache cache) {
        SyncResponse.ActiveStaffMember staffMember = getStaffMember(issuer, cache);
        return staffMember != null ? staffMember.getPermissions() : List.of();
    }
    
    /**
     * Format punishment type name to permission string
     * @param punishmentTypeName The punishment type name (e.g. "Chat Spam")
     * @return The permission string (e.g. "punishment.apply.chat-spam")
     */
    public static String formatPunishmentPermission(String punishmentTypeName) {
        return "punishment.apply." + punishmentTypeName.toLowerCase().replace(" ", "-");
    }
}