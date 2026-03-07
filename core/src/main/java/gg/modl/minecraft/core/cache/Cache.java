package gg.modl.minecraft.core.cache;

import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.config.PunishGuiConfig;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class Cache {
    private static final long TEXTURE_TTL_MS = 10 * 60 * 1000;
    private static final int TEXTURE_CACHE_MAX_SIZE = 500;

    @Getter private final PlayerProfileRegistry registry;
    private final Map<UUID, StaffPermissions> staffPermissionsCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedTexture> skinTextureCache = new ConcurrentHashMap<>();
    @Getter private volatile PunishmentTypesResponse cachedPunishmentTypes;
    @Getter private volatile PunishGuiConfig cachedPunishGuiConfig;
    @Getter private volatile ReportGuiConfig cachedReportGuiConfig;
    @Getter @Setter private volatile Map<Integer, String> punishmentTypeItems;

    @Getter @Setter private boolean queryMojang;

    public PlayerProfile getPlayerProfile(UUID uuid) {
        return registry.getProfile(uuid);
    }

    public void setOffline(UUID playerUuid) {
        skinTextureCache.remove(playerUuid);
    }

    public void cacheSkinTexture(UUID playerUuid, String textureValue) {
        if (textureValue == null) return;
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) evictExpiredTextures();
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) {
            Iterator<UUID> it = skinTextureCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        skinTextureCache.put(playerUuid, new CachedTexture(textureValue, System.currentTimeMillis()));
    }

    public String getSkinTexture(UUID playerUuid) {
        CachedTexture entry = skinTextureCache.get(playerUuid);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.cachedAt > TEXTURE_TTL_MS) {
            skinTextureCache.remove(playerUuid);
            return null;
        }
        return entry.value;
    }

    private void evictExpiredTextures() {
        long now = System.currentTimeMillis();
        skinTextureCache.entrySet().removeIf(e -> now - e.getValue().cachedAt > TEXTURE_TTL_MS);
    }

    @AllArgsConstructor
    private static class CachedTexture {
        final String value;
        final long cachedAt;
    }

    public String getStaffDisplayName(UUID playerUuid) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        if (profile != null) {
            SyncResponse.ActiveStaffMember staff = profile.getStaffMember();
            if (staff != null && !staff.getStaffUsername().isEmpty()) return staff.getStaffUsername();
        }

        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null && staffPerms.getStaffUsername() != null && !staffPerms.getStaffUsername().isEmpty()) return staffPerms.getStaffUsername();
        return null;
    }

    public String getStaffId(UUID playerUuid) {
        PlayerProfile profile = registry.getProfile(playerUuid);
        if (profile != null) {
            SyncResponse.ActiveStaffMember staff = profile.getStaffMember();
            if (staff != null) return staff.getStaffId();
        }

        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null && staffPerms.getStaffId() != null) return staffPerms.getStaffId();
        return null;
    }

    public String getDisplayName(UUID playerUuid, String fallback) {
        String panelName = getStaffDisplayName(playerUuid);
        return panelName != null ? panelName : fallback;
    }

    public boolean hasPermission(UUID playerUuid, String permission) {
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null) return checkRoleAndPermissions(staffPerms.getStaffRole(), staffPerms.getPermissions(), permission);

        PlayerProfile profile = registry.getProfile(playerUuid);
        if (profile != null) {
            SyncResponse.ActiveStaffMember staffMember = profile.getStaffMember();
            if (staffMember != null) return checkRoleAndPermissions(staffMember.getStaffRole(), staffMember.getPermissions(), permission);
        }

        return false;
    }

    private boolean checkRoleAndPermissions(String role, List<String> permissions, String requested) {
        if ("Super Admin".equalsIgnoreCase(role)) return true;
        for (String perm : permissions) {
            if (perm.equals(requested)) return true;
            if (requested.length() > perm.length()
                    && requested.charAt(perm.length()) == '.'
                    && requested.startsWith(perm)) return true;
        }
        return false;
    }

    public void cacheStaffPermissions(UUID playerUuid, String staffUsername, String staffId, String staffRole, List<String> permissions) {
        staffPermissionsCache.put(playerUuid, new StaffPermissions(staffUsername, staffId, staffRole, permissions));
    }

    public void removeStaffPermissions(UUID playerUuid) {
        staffPermissionsCache.remove(playerUuid);
    }

    public void clearStaffPermissions() {
        staffPermissionsCache.clear();
    }

    public boolean isStaffMemberByPermissions(UUID playerUuid) {
        return staffPermissionsCache.containsKey(playerUuid);
    }

    public String getStaffRole(UUID playerUuid) {
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        return staffPerms != null ? staffPerms.getStaffRole() : null;
    }

    public void clear() {
        registry.clear();
        staffPermissionsCache.clear();
        skinTextureCache.clear();
        cachedPunishmentTypes = null;
        cachedPunishGuiConfig = null;
        punishmentTypeItems = null;
    }

    @Getter
    public static class StaffPermissions {
        private final String staffUsername, staffId, staffRole;
        private final List<String> permissions;

        public StaffPermissions(String staffUsername, String staffId, String staffRole, List<String> permissions) {
            this.staffUsername = staffUsername;
            this.staffId = staffId;
            this.staffRole = staffRole;
            this.permissions = permissions != null ? permissions : List.of();
        }
    }

    public void cachePunishmentTypes(PunishmentTypesResponse response) {
        this.cachedPunishmentTypes = response;
    }

    public void clearPunishmentTypes() {
        this.cachedPunishmentTypes = null;
    }

    public void cachePunishGuiConfig(PunishGuiConfig config) {
        this.cachedPunishGuiConfig = config;
    }

    public void clearPunishGuiConfig() {
        this.cachedPunishGuiConfig = null;
    }

    public void cacheReportGuiConfig(ReportGuiConfig config) {
        this.cachedReportGuiConfig = config;
    }

}
