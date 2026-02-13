package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.SimplePunishment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncResponse {
    @NotNull
    private String timestamp;
    
    @NotNull
    private SyncData data;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SyncData {
        @NotNull
        private List<PendingPunishment> pendingPunishments;

        @NotNull
        private List<PendingPunishment> recentlyStartedPunishments;

        @NotNull
        private List<ModifiedPunishment> recentlyModifiedPunishments;

        @NotNull
        private List<PlayerNotification> playerNotifications;

        @NotNull
        private List<ActiveStaffMember> activeStaffMembers;

        private MigrationTask migrationTask;

        private List<StaffNotification> staffNotifications;

        private Long staffPermissionsUpdatedAt;

        private Long punishmentTypesUpdatedAt;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PendingPunishment {
        @NotNull
        private String minecraftUuid;
        
        @NotNull
        private String username;
        
        @NotNull
        private SimplePunishment punishment;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModifiedPunishment {
        @NotNull
        private String minecraftUuid;
        
        @NotNull
        private String username;
        
        @NotNull
        private PunishmentWithModifications punishment;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PunishmentWithModifications {
        @NotNull
        private String id;
        
        @NotNull
        private List<PunishmentModification> modifications;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PunishmentModification {
        @NotNull
        private String type;

        private Long timestamp;

        private Long effectiveDuration;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PlayerNotification {
        @NotNull
        private String id;
        
        @NotNull
        private String message;
        
        @NotNull
        private String type;
        
        private Long timestamp;
        
        private String targetPlayerUuid;
        
        private java.util.Map<String, Object> data;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ActiveStaffMember {
        @NotNull
        private String minecraftUuid;
        
        @NotNull
        private String minecraftUsername;
        
        @NotNull
        private String staffUsername;
        
        @NotNull
        private String staffRole;
        
        @NotNull
        private List<String> permissions;
        
        @NotNull
        private String email;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StaffNotification {
        private String id;
        private String type;    // TICKET_CREATED, PUNISHMENT_ISSUED, PUNISHMENT_PARDONED
        private String message;
        private Long timestamp;
        private java.util.Map<String, Object> data;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MigrationTask {
        @NotNull
        private String taskId;
        
        @NotNull
        private String type;
    }
}