package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class StaffPermissionsResponse {
    private StaffData data;
    private int status;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StaffData {
        private List<StaffMember> staff;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StaffMember {
        private String minecraftUuid, minecraftUsername, staffUsername, staffId, staffRole, email;
        private List<String> permissions;
    }
}
