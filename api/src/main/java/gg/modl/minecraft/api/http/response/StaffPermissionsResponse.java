package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffPermissionsResponse {
    private int status;
    private StaffData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffData {
        private List<StaffMember> staff;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffMember {
        private String minecraftUuid;
        private String minecraftUsername;
        private String staffUsername;
        private String staffRole;
        private List<String> permissions;
        private String email;
    }
}