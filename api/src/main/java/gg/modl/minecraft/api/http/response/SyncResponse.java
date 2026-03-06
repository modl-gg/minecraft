package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.SimplePunishment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SyncResponse {
    private @NotNull String timestamp;
    private @NotNull SyncData data;
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SyncData {
        private @NotNull List<PendingPunishment> pendingPunishments;
        private @NotNull List<PendingPunishment> recentlyStartedPunishments;
        private @NotNull List<ModifiedPunishment> recentlyModifiedPunishments;
        private @NotNull List<PlayerNotification> playerNotifications;
        private @NotNull List<ActiveStaffMember> activeStaffMembers;
        private MigrationTask migrationTask;
        private List<StaffNotification> staffNotifications;
        private List<PendingStatWipe> pendingStatWipes;
        private Long staffPermissionsUpdatedAt;
        private Long punishmentTypesUpdatedAt;
        private List<Staff2faVerification> staff2faVerifications;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PendingPunishment {
        private @NotNull String minecraftUuid;
        private @NotNull String username;
        private @NotNull SimplePunishment punishment;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ModifiedPunishment {
        private @NotNull String minecraftUuid;
        private @NotNull String username;
        private @NotNull PunishmentWithModifications punishment;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PunishmentWithModifications {
        private @NotNull String id;
        private @NotNull List<PunishmentModification> modifications;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PunishmentModification {
        private @NotNull String type;
        private Long timestamp;
        private Long effectiveDuration;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlayerNotification {
        private @NotNull String id;
        private @NotNull String message;
        private @NotNull String type;
        private Long timestamp;
        private String targetPlayerUuid;
        private java.util.Map<String, Object> data;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ActiveStaffMember {
        private @NotNull String minecraftUuid;
        private @NotNull String minecraftUsername;
        private @NotNull String staffUsername;
        private @NotNull String staffRole;
        private @NotNull List<String> permissions;
        private @NotNull String email;
        private Boolean twoFactorSessionValid;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StaffNotification {
        private String id;
        private String type;
        private String message;
        private Long timestamp;
        private java.util.Map<String, Object> data;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PendingStatWipe {
        private @NotNull String minecraftUuid;
        private @NotNull String username;
        private @NotNull String punishmentId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MigrationTask {
        private @NotNull String taskId;
        private @NotNull String type;
    }

    @Data @NoArgsConstructor
    public static class Staff2faVerification {
        private String minecraftUuid;
    }
}
