package gg.modl.minecraft.core.util;

import revxrsal.commands.command.CommandActor;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.cache.CachedProfile;

import java.util.UUID;

public final class PermissionUtil {
    private PermissionUtil() {}
    public static boolean hasPermission(CommandActor actor, Cache cache, String permission) {
        if (actor.uniqueId() == null) return true;
        return cache.hasPermission(actor.uniqueId(), permission);
    }

    public static boolean hasAnyPermission(CommandActor actor, Cache cache, String... permissions) {
        if (actor.uniqueId() == null) return true;
        for (String permission : permissions) {
            if (hasPermission(actor, cache, permission)) return true;
        }
        return false;
    }

    public static SyncResponse.ActiveStaffMember getStaffMember(CommandActor actor, Cache cache) {
        if (actor.uniqueId() == null) return null;
        CachedProfile profile = cache.getPlayerProfile(actor.uniqueId());
        return profile != null ? profile.getStaffMember() : null;
    }

    public static boolean isStaff(CommandActor actor, Cache cache) {
        if (actor.uniqueId() == null) return false;
        return isStaff(actor.uniqueId(), cache);
    }

    public static boolean isStaff(UUID playerUuid, Cache cache) {
        if (cache == null) return false;
        if (cache.isStaffMemberByPermissions(playerUuid)) return true;
        CachedProfile profile = cache.getPlayerProfile(playerUuid);
        return profile != null && profile.getStaffMember() != null;
    }

    public static String formatPunishmentPermission(String punishmentTypeName) {
        return "punishment.apply." + punishmentTypeName.toLowerCase().replace(" ", "-");
    }
}
