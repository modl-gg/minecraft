package gg.modl.minecraft.core.impl.cache;

import gg.modl.minecraft.api.Punishment;
import gg.modl.minecraft.api.SimplePunishment;
import gg.modl.minecraft.api.http.response.SyncResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    
    private final Map<UUID, CachedPlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, StaffPermissions> staffPermissionsCache = new ConcurrentHashMap<>();
    // Notification storage - maps player UUID to list of pending notifications
    private final Map<UUID, List<PendingNotification>> pendingNotificationsCache = new ConcurrentHashMap<>();
    
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
            return staffPerms.getPermissions().contains(permission);
        }
        
        // Fallback to old sync-based staff member (for backward compatibility)
        SyncResponse.ActiveStaffMember staffMember = getStaffMember(playerUuid);
        return staffMember != null && staffMember.getPermissions().contains(permission);
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
        return pendingNotificationsCache.getOrDefault(playerUuid, new ArrayList<>());
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
        private Punishment mute;
        private SimplePunishment simpleMute;
        private Punishment ban;
        private SimplePunishment simpleBan;
        private SyncResponse.ActiveStaffMember staffMember;
        
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