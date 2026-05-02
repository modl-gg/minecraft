package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.SimplePunishment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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
        private List<Staff2faVerification> staff2faVerifications;
        private Long staffPermissionsUpdatedAt, punishmentTypesUpdatedAt;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PendingPunishment {
        private @NotNull String minecraftUuid, username;
        private @NotNull SimplePunishment punishment;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ModifiedPunishment {
        private @NotNull String minecraftUuid, username;
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
        private Long timestamp, effectiveDuration;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlayerNotification {
        private @NotNull String id, message, type;
        private String targetPlayerUuid;
        private Map<String, Object> data;
        private Long timestamp;
    }
    
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ActiveStaffMember {
        private @NotNull String minecraftUuid, minecraftUsername, staffUsername, staffRole, email, staffId;
        private @NotNull List<String> permissions;
        private Boolean twoFactorSessionValid;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StaffNotification {
        private String id, type, message;
        private Map<String, Object> data;
        private Long timestamp;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PendingStatWipe {
        private @NotNull String minecraftUuid, username, punishmentId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MigrationTask {
        private @NotNull String taskId, type;
    }

    @Data @NoArgsConstructor
    public static class Staff2faVerification {
        private String minecraftUuid;
    }
}
