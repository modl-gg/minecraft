package gg.modl.minecraft.core.util;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.PlayerProfile;

import java.util.UUID;

public class PermissionUtil {
    public static boolean hasPermission(CommandIssuer issuer, Cache cache, String permission) {
        if (!issuer.isPlayer()) return true;
        return cache.hasPermission(issuer.getUniqueId(), permission);
    }

    public static boolean hasAnyPermission(CommandIssuer issuer, Cache cache, String... permissions) {
        if (!issuer.isPlayer()) return true;
        for (String permission : permissions) {
            if (hasPermission(issuer, cache, permission)) return true;
        }
        return false;
    }

    public static SyncResponse.ActiveStaffMember getStaffMember(CommandIssuer issuer, Cache cache) {
        if (!issuer.isPlayer()) return null;
        PlayerProfile profile = cache.getPlayerProfile(issuer.getUniqueId());
        return profile != null ? profile.getStaffMember() : null;
    }

    public static boolean isStaff(CommandIssuer issuer, Cache cache) {
        if (!issuer.isPlayer()) return false;
        return isStaff(issuer.getUniqueId(), cache);
    }

    public static boolean isStaff(UUID playerUuid, Cache cache) {
        if (cache == null) return false;
        if (cache.isStaffMemberByPermissions(playerUuid)) return true;
        PlayerProfile profile = cache.getPlayerProfile(playerUuid);
        return profile != null && profile.getStaffMember() != null;
    }

    public static String formatPunishmentPermission(String punishmentTypeName) {
        return "punishment.apply." + punishmentTypeName.toLowerCase().replace(" ", "-");
    }
}
