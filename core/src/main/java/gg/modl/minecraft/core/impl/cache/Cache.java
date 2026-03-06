package gg.modl.minecraft.core.impl.cache;

import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import gg.modl.minecraft.core.config.PunishGuiConfig;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    private static final long NOTIFICATION_EXPIRY_MS = 24 * 60 * 60 * 1000L;
    private static final long TEXTURE_TTL_MS = 10 * 60 * 1000;
    private static final int TEXTURE_CACHE_MAX_SIZE = 500;

    private final Map<UUID, CachedPlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, StaffPermissions> staffPermissionsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<PendingNotification>> pendingNotificationsCache = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CachedTexture> skinTextureCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, StaffPreferences> staffPreferencesCache = new ConcurrentHashMap<>();
    @Getter private volatile PunishmentTypesResponse cachedPunishmentTypes;
    @Getter private volatile PunishGuiConfig cachedPunishGuiConfig;
    @Getter private volatile ReportGuiConfig cachedReportGuiConfig;
    @Getter @Setter private volatile Map<Integer, String> punishmentTypeItems;
    @Getter @Setter private boolean queryMojang;

    public void cacheMute(UUID playerUuid, SimplePunishment mute) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setSimpleMute(mute);
    }

    public boolean isMuted(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data == null || data.getSimpleMute() == null) return false;
        if (data.getSimpleMute().isExpired()) {
            removeMute(playerUuid);
            return false;
        }
        return true;
    }

    public SimplePunishment getSimpleMute(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getSimpleMute() : null;
    }

    public void removeMute(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data != null) {
            data.setSimpleMute(null);
            if (data.isEmpty()) cache.remove(playerUuid);
        }
    }

    public boolean isBanned(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data == null || data.getSimpleBan() == null) return false;
        if (data.getSimpleBan().isExpired()) {
            removeBan(playerUuid);
            return false;
        }
        return true;
    }

    public SimplePunishment getSimpleBan(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getSimpleBan() : null;
    }

    public void removeBan(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data != null) {
            data.setSimpleBan(null);
            if (data.isEmpty()) cache.remove(playerUuid);
        }
    }

    public void removePlayer(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public void setOnline(UUID playerUuid) {
        onlinePlayers.add(playerUuid);
        joinTimes.put(playerUuid, System.currentTimeMillis());
    }

    public void setOffline(UUID playerUuid) {
        onlinePlayers.remove(playerUuid);
        joinTimes.remove(playerUuid);
        skinTextureCache.remove(playerUuid);
        staffPreferencesCache.remove(playerUuid);
        pendingNotificationsCache.remove(playerUuid);
    }

    public boolean isOnline(UUID playerUuid) {
        return onlinePlayers.contains(playerUuid);
    }

    public long getSessionDuration(UUID playerUuid) {
        Long joinTime = joinTimes.get(playerUuid);
        if (joinTime == null) return 0;
        return System.currentTimeMillis() - joinTime;
    }

    public Set<UUID> getOnlinePlayers() {
        return Collections.unmodifiableSet(onlinePlayers);
    }

    public synchronized void cacheSkinTexture(UUID playerUuid, String textureValue) {
        if (textureValue == null) return;
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) evictExpiredTextures();
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) {
            long oldestTime = Long.MAX_VALUE;
            UUID oldestUuid = null;
            for (Map.Entry<UUID, CachedTexture> entry : skinTextureCache.entrySet()) {
                if (entry.getValue().cachedAt < oldestTime) {
                    oldestTime = entry.getValue().cachedAt;
                    oldestUuid = entry.getKey();
                }
            }
            if (oldestUuid != null) skinTextureCache.remove(oldestUuid);
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

    public void cacheStaffMember(UUID playerUuid, SyncResponse.ActiveStaffMember staffMember) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setStaffMember(staffMember);
    }

    public SyncResponse.ActiveStaffMember getStaffMember(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getStaffMember() : null;
    }

    public boolean isStaffMember(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null && data.getStaffMember() != null;
    }

    public String getStaffDisplayName(UUID playerUuid) {
        SyncResponse.ActiveStaffMember staff = getStaffMember(playerUuid);
        if (staff != null && !staff.getStaffUsername().isEmpty()) return staff.getStaffUsername();

        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null && staffPerms.getStaffUsername() != null && !staffPerms.getStaffUsername().isEmpty()) return staffPerms.getStaffUsername();
        return null;
    }

    public String getDisplayName(UUID playerUuid, String fallback) {
        String panelName = getStaffDisplayName(playerUuid);
        return panelName != null ? panelName : fallback;
    }

    public boolean hasPermission(UUID playerUuid, String permission) {
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null) return checkRoleAndPermissions(staffPerms.getStaffRole(), staffPerms.getPermissions(), permission);

        SyncResponse.ActiveStaffMember staffMember = getStaffMember(playerUuid);
        if (staffMember != null) return checkRoleAndPermissions(staffMember.getStaffRole(), staffMember.getPermissions(), permission);

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

    public void cacheStaffPermissions(UUID playerUuid, String staffUsername, String staffRole, List<String> permissions) {
        staffPermissionsCache.put(playerUuid, new StaffPermissions(staffUsername, staffRole, permissions));
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

    public void cacheNotification(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        PendingNotification pending = new PendingNotification(
            notification.getId(),
            notification.getMessage(),
            notification.getType(),
            notification.getTimestamp(),
            System.currentTimeMillis(),
            notification.getData()
        );

        pendingNotificationsCache.computeIfAbsent(playerUuid, k -> Collections.synchronizedList(new ArrayList<>())).add(pending);
    }

    public List<PendingNotification> getPendingNotifications(UUID playerUuid) {
        List<PendingNotification> notifications = pendingNotificationsCache.get(playerUuid);
        if (notifications == null) return new ArrayList<>();
        notifications.removeIf(PendingNotification::isExpired);
        if (notifications.isEmpty()) pendingNotificationsCache.remove(playerUuid);
        return notifications;
    }

    public boolean removeNotification(UUID playerUuid, String notificationId) {
        List<PendingNotification> notifications = pendingNotificationsCache.get(playerUuid);
        if (notifications != null) {
            boolean removed = notifications.removeIf(n -> n.getId().equals(notificationId));
            if (notifications.isEmpty()) pendingNotificationsCache.remove(playerUuid);
            return removed;
        }
        return false;
    }

    public void cleanupExpiredNotifications() {
        pendingNotificationsCache.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(PendingNotification::isExpired);
            return entry.getValue().isEmpty();
        });
    }

    public void clear() {
        cache.clear();
        pendingNotificationsCache.clear();
        onlinePlayers.clear();
        joinTimes.clear();
        skinTextureCache.clear();
        staffPreferencesCache.clear();
        staffPermissionsCache.clear();
        cachedPunishmentTypes = null;
        cachedPunishGuiConfig = null;
        punishmentTypeItems = null;
    }

    public CachedPlayerData getCachedPlayerData(UUID playerUuid) {
        return cache.get(playerUuid);
    }

    @Getter @Setter
    public static class CachedPlayerData {
        private volatile SimplePunishment simpleMute;
        private volatile SimplePunishment simpleBan;
        private volatile SyncResponse.ActiveStaffMember staffMember;

        public boolean isEmpty() {
            return simpleMute == null && simpleBan == null && staffMember == null;
        }
    }

    @Getter
    public static class StaffPermissions {
        private final String staffUsername;
        private final String staffRole;
        private final List<String> permissions;

        public StaffPermissions(String staffUsername, String staffRole, List<String> permissions) {
            this.staffUsername = staffUsername;
            this.staffRole = staffRole;
            this.permissions = permissions != null ? permissions : List.of();
        }
    }

    public StaffPreferences getStaffPreferences(UUID playerUuid) {
        return staffPreferencesCache.computeIfAbsent(playerUuid, k -> new StaffPreferences());
    }

    public boolean isStaffNotificationsEnabled(UUID playerUuid) {
        return getStaffPreferences(playerUuid).isStaffNotificationsEnabled();
    }

    public void setStaffNotificationsEnabled(UUID playerUuid, boolean enabled) {
        getStaffPreferences(playerUuid).setStaffNotificationsEnabled(enabled);
    }

    @Getter @Setter
    public static class StaffPreferences {
        private boolean staffNotificationsEnabled = true;
    }

    @Getter
    public static class PendingNotification {
        private final String id;
        private final String message;
        private final String type;
        private final Long timestamp;
        private final long cachedTime;
        private final Map<String, Object> data;

        public PendingNotification(String id, String message, String type, Long timestamp, long cachedTime, Map<String, Object> data) {
            this.id = id;
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
            this.cachedTime = cachedTime;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - cachedTime) > NOTIFICATION_EXPIRY_MS;
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
