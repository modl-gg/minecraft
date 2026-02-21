package gg.modl.minecraft.core.impl.cache;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.response.PunishmentTypesResponse;
import gg.modl.minecraft.api.http.response.SyncResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class Cache {

    private final Map<UUID, CachedPlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, StaffPermissions> staffPermissionsCache = new ConcurrentHashMap<>();
    // Notification storage - maps player UUID to list of pending notifications
    private final Map<UUID, List<PendingNotification>> pendingNotificationsCache = new ConcurrentHashMap<>();
    // Online players tracking
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    // Skin texture cache - maps player UUID to base64 texture value with TTL
    private static final long TEXTURE_TTL_MS = 10 * 60 * 1000; // 10 minutes
    private static final int TEXTURE_CACHE_MAX_SIZE = 500;
    private final Map<UUID, CachedTexture> skinTextureCache = new ConcurrentHashMap<>();
    // Whether Mojang API queries are enabled for on-demand texture fetches
    @Getter @Setter
    private boolean queryMojang;
    
    public void cacheMute(UUID playerUuid, Punishment mute) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setMute(mute);
    }
    
    public void cacheMute(UUID playerUuid, SimplePunishment mute) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setSimpleMute(mute);
    }
    
    public void cacheBan(UUID playerUuid, Punishment ban) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setBan(ban);
    }
    
    public void cacheBan(UUID playerUuid, SimplePunishment ban) {
        cache.computeIfAbsent(playerUuid, k -> new CachedPlayerData()).setSimpleBan(ban);
    }

    public boolean isMuted(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data == null) {
            return false;
        }
        
        // Check SimplePunishment first (new format)
        if (data.getSimpleMute() != null) {
            SimplePunishment mute = data.getSimpleMute();
            if (mute.isExpired()) {
                removeMute(playerUuid);
                return false;
            }
            return true;
        }
        
        // Fallback to old Punishment format
        if (data.getMute() != null) {
            Punishment mute = data.getMute();
            if (mute.getExpires() != null && mute.getExpires().before(new java.util.Date())) {
                removeMute(playerUuid);
                return false;
            }
            return true;
        }
        
        return false;
    }
    
    public Punishment getMute(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getMute() : null;
    }

    public SimplePunishment getSimpleMute(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getSimpleMute() : null;
    }

    public void removeMute(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data != null) {
            data.setMute(null);
            data.setSimpleMute(null);
            if (data.isEmpty()) {
                cache.remove(playerUuid);
            }
        }
    }
    
    public boolean isBanned(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data == null) {
            return false;
        }
        
        // Check SimplePunishment first (new format)
        if (data.getSimpleBan() != null) {
            SimplePunishment ban = data.getSimpleBan();
            if (ban.isExpired()) {
                removeBan(playerUuid);
                return false;
            }
            return true;
        }
        
        // Fallback to old Punishment format
        if (data.getBan() != null) {
            Punishment ban = data.getBan();
            if (ban.getExpires() != null && ban.getExpires().before(new java.util.Date())) {
                removeBan(playerUuid);
                return false;
            }
            return true;
        }
        
        return false;
    }
    
    public Punishment getBan(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getBan() : null;
    }

    public SimplePunishment getSimpleBan(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        return data != null ? data.getSimpleBan() : null;
    }

    public void removeBan(UUID playerUuid) {
        CachedPlayerData data = cache.get(playerUuid);
        if (data != null) {
            data.setBan(null);
            data.setSimpleBan(null);
            if (data.isEmpty()) {
                cache.remove(playerUuid);
            }
        }
    }
    
    public void removePlayer(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    // ==================== ONLINE PLAYER TRACKING ====================

    // Join time tracking - maps player UUID to join timestamp
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    /**
     * Mark a player as online and record join time
     */
    public void setOnline(UUID playerUuid) {
        onlinePlayers.add(playerUuid);
        joinTimes.put(playerUuid, System.currentTimeMillis());
    }

    /**
     * Mark a player as offline and remove join time
     */
    public void setOffline(UUID playerUuid) {
        onlinePlayers.remove(playerUuid);
        joinTimes.remove(playerUuid);
        skinTextureCache.remove(playerUuid);
        staffPreferencesCache.remove(playerUuid);
        pendingNotificationsCache.remove(playerUuid);
    }

    /**
     * Check if a player is online
     */
    public boolean isOnline(UUID playerUuid) {
        return onlinePlayers.contains(playerUuid);
    }

    /**
     * Get the join time for a player (when they came online)
     * @return Join time in milliseconds, or null if not online
     */
    public Long getJoinTime(UUID playerUuid) {
        return joinTimes.get(playerUuid);
    }

    /**
     * Get session duration for an online player
     * @return Session duration in milliseconds, or 0 if not online
     */
    public long getSessionDuration(UUID playerUuid) {
        Long joinTime = joinTimes.get(playerUuid);
        if (joinTime == null) return 0;
        return System.currentTimeMillis() - joinTime;
    }

    /**
     * Get all online players
     */
    public Set<UUID> getOnlinePlayers() {
        return Collections.unmodifiableSet(onlinePlayers);
    }

    // ==================== SKIN TEXTURE CACHE ====================

    /**
     * Cache a skin texture for a player.
     * Entries expire after {@link #TEXTURE_TTL_MS} and the cache is capped at {@link #TEXTURE_CACHE_MAX_SIZE}.
     * @param playerUuid The player's UUID
     * @param textureValue The base64 encoded texture value
     */
    public void cacheSkinTexture(UUID playerUuid, String textureValue) {
        if (textureValue == null) return;
        // Evict expired entries if we're at capacity
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) {
            evictExpiredTextures();
        }
        // If still at capacity after eviction, remove oldest entry
        if (skinTextureCache.size() >= TEXTURE_CACHE_MAX_SIZE) {
            skinTextureCache.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(e -> e.getValue().cachedAt))
                    .ifPresent(e -> skinTextureCache.remove(e.getKey()));
        }
        skinTextureCache.put(playerUuid, new CachedTexture(textureValue, System.currentTimeMillis()));
    }

    /**
     * Get the cached skin texture for a player.
     * Returns null if not cached or if the entry has expired.
     * @param playerUuid The player's UUID
     * @return The base64 encoded texture value, or null if not cached/expired
     */
    public String getSkinTexture(UUID playerUuid) {
        CachedTexture entry = skinTextureCache.get(playerUuid);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.cachedAt > TEXTURE_TTL_MS) {
            skinTextureCache.remove(playerUuid);
            return null;
        }
        return entry.value;
    }

    /**
     * Remove expired texture cache entries.
     */
    private void evictExpiredTextures() {
        long now = System.currentTimeMillis();
        skinTextureCache.entrySet().removeIf(e -> now - e.getValue().cachedAt > TEXTURE_TTL_MS);
    }

    private static class CachedTexture {
        final String value;
        final long cachedAt;

        CachedTexture(String value, long cachedAt) {
            this.value = value;
            this.cachedAt = cachedAt;
        }
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
    
    public boolean hasPermission(UUID playerUuid, String permission) {
        // Check new staff permissions cache first
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null) {
            return matchesPermission(staffPerms.getPermissions(), permission);
        }

        // Fallback to old sync-based staff member (for backward compatibility)
        SyncResponse.ActiveStaffMember staffMember = getStaffMember(playerUuid);
        return staffMember != null && matchesPermission(staffMember.getPermissions(), permission);
    }

    /**
     * Check if a permission list grants the requested permission.
     * Supports parent matching: having "punishment.modify" grants "punishment.modify.pardon", etc.
     */
    private boolean matchesPermission(List<String> permissions, String requested) {
        for (String perm : permissions) {
            if (perm.equals(requested)) return true;
            // Parent permission grants all children (e.g. "punishment.modify" grants "punishment.modify.note")
            if (requested.startsWith(perm + ".")) return true;
        }
        return false;
    }

    /**
     * Check if a player has any punishment.apply permission
     */
    public boolean hasAnyPunishmentPermission(UUID playerUuid) {
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        if (staffPerms != null) {
            return staffPerms.getPermissions().stream()
                    .anyMatch(p -> p.startsWith("punishment.apply."));
        }

        SyncResponse.ActiveStaffMember staffMember = getStaffMember(playerUuid);
        if (staffMember != null) {
            return staffMember.getPermissions().stream()
                    .anyMatch(p -> p.startsWith("punishment.apply."));
        }

        return false;
    }

    public void cacheStaffPermissions(UUID playerUuid, String staffRole, List<String> permissions) {
        staffPermissionsCache.put(playerUuid, new StaffPermissions(staffRole, permissions));
    }
    
    public void clearStaffPermissions() {
        staffPermissionsCache.clear();
    }
    
    public boolean isStaffMemberByPermissions(UUID playerUuid) {
        return staffPermissionsCache.containsKey(playerUuid);
    }

    /**
     * Get the staff role name for a player
     * @return The staff role name, or null if not a staff member
     */
    public String getStaffRole(UUID playerUuid) {
        StaffPermissions staffPerms = staffPermissionsCache.get(playerUuid);
        return staffPerms != null ? staffPerms.getStaffRole() : null;
    }
    
    // ==================== NOTIFICATION MANAGEMENT ====================
    
    /**
     * Cache a pending notification for a player
     */
    public void cacheNotification(UUID playerUuid, SyncResponse.PlayerNotification notification) {
        PendingNotification pending = new PendingNotification(
            notification.getId(),
            notification.getMessage(),
            notification.getType(),
            notification.getTimestamp(),
            System.currentTimeMillis(), // Cache time
            notification.getData() // Preserve additional data like ticket URLs
        );
        
        pendingNotificationsCache.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(pending);
    }
    
    /**
     * Get all pending notifications for a player
     */
    public List<PendingNotification> getPendingNotifications(UUID playerUuid) {
        List<PendingNotification> notifications = pendingNotificationsCache.get(playerUuid);
        if (notifications == null) return new ArrayList<>();
        notifications.removeIf(PendingNotification::isExpired);
        if (notifications.isEmpty()) {
            pendingNotificationsCache.remove(playerUuid);
        }
        return notifications;
    }
    
    /**
     * Remove a specific notification by ID
     */
    public boolean removeNotification(UUID playerUuid, String notificationId) {
        List<PendingNotification> notifications = pendingNotificationsCache.get(playerUuid);
        if (notifications != null) {
            boolean removed = notifications.removeIf(n -> n.getId().equals(notificationId));
            if (notifications.isEmpty()) {
                pendingNotificationsCache.remove(playerUuid);
            }
            return removed;
        }
        return false;
    }
    
    /**
     * Remove all notifications for a player
     */
    public List<PendingNotification> clearNotifications(UUID playerUuid) {
        return pendingNotificationsCache.remove(playerUuid);
    }
    
    /**
     * Get notification count for a player
     */
    public int getNotificationCount(UUID playerUuid) {
        List<PendingNotification> notifications = pendingNotificationsCache.get(playerUuid);
        return notifications != null ? notifications.size() : 0;
    }
    
    /**
     * Check if player has any pending notifications
     */
    public boolean hasPendingNotifications(UUID playerUuid) {
        return getNotificationCount(playerUuid) > 0;
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
    }
    
    public int size() {
        return cache.size();
    }
    
    /**
     * Get the internal cache map (for platform-specific access)
     */
    public Map<UUID, CachedPlayerData> getCache() {
        return cache;
    }
    
    @Getter
    @Setter
    public static class CachedPlayerData {
        private volatile Punishment mute;
        private volatile SimplePunishment simpleMute;
        private volatile Punishment ban;
        private volatile SimplePunishment simpleBan;
        private volatile SyncResponse.ActiveStaffMember staffMember;

        public boolean isEmpty() {
            return mute == null && simpleMute == null && ban == null && simpleBan == null && staffMember == null;
        }
    }
    
    @Getter
    public static class StaffPermissions {
        private final String staffRole;
        private final List<String> permissions;

        public StaffPermissions(String staffRole, List<String> permissions) {
            this.staffRole = staffRole;
            this.permissions = permissions != null ? permissions : List.of();
        }
    }

    // ==================== STAFF PREFERENCES ====================

    // Staff preferences storage - maps player UUID to their preferences
    private final Map<UUID, StaffPreferences> staffPreferencesCache = new ConcurrentHashMap<>();

    /**
     * Get staff preferences for a player, creating default if not exists
     */
    public StaffPreferences getStaffPreferences(UUID playerUuid) {
        return staffPreferencesCache.computeIfAbsent(playerUuid, k -> new StaffPreferences());
    }

    /**
     * Check if staff notifications are enabled for a staff member
     */
    public boolean isStaffNotificationsEnabled(UUID playerUuid) {
        return getStaffPreferences(playerUuid).isStaffNotificationsEnabled();
    }

    /**
     * Set staff notifications preference for a staff member
     */
    public void setStaffNotificationsEnabled(UUID playerUuid, boolean enabled) {
        getStaffPreferences(playerUuid).setStaffNotificationsEnabled(enabled);
    }

    @Getter
    @Setter
    public static class StaffPreferences {
        private boolean staffNotificationsEnabled = true; // Default enabled

        public StaffPreferences() {}
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
        
        /**
         * Check if this notification has expired (older than 24 hours)
         */
        public boolean isExpired() {
            return (System.currentTimeMillis() - cachedTime) > 24 * 60 * 60 * 1000; // 24 hours
        }
    }
    
    // ==================== PUNISHMENT TYPES CACHE ====================

    private volatile PunishmentTypesResponse cachedPunishmentTypes;

    /**
     * Cache the punishment types response.
     */
    public void cachePunishmentTypes(PunishmentTypesResponse response) {
        this.cachedPunishmentTypes = response;
    }

    /**
     * Get cached punishment types, or null if not cached.
     */
    public PunishmentTypesResponse getCachedPunishmentTypes() {
        return cachedPunishmentTypes;
    }

    /**
     * Clear the cached punishment types (e.g., on reload).
     */
    public void clearPunishmentTypes() {
        this.cachedPunishmentTypes = null;
    }

    // ==================== PUNISH GUI CONFIG CACHE ====================

    private volatile gg.modl.minecraft.core.config.PunishGuiConfig cachedPunishGuiConfig;

    /**
     * Cache the punish GUI config.
     */
    public void cachePunishGuiConfig(gg.modl.minecraft.core.config.PunishGuiConfig config) {
        this.cachedPunishGuiConfig = config;
    }

    /**
     * Get cached punish GUI config, or null if not cached.
     */
    public gg.modl.minecraft.core.config.PunishGuiConfig getCachedPunishGuiConfig() {
        return cachedPunishGuiConfig;
    }

    /**
     * Clear the cached punish GUI config (e.g., on reload).
     */
    public void clearPunishGuiConfig() {
        this.cachedPunishGuiConfig = null;
    }

    /**
     * Get the number of staff members with cached permissions
     */
    public int getStaffCount() {
        return staffPermissionsCache.size();
    }
    
    /**
     * Get the number of players with cached punishment data
     */
    public int getCachedPlayerCount() {
        return cache.size();
    }
}