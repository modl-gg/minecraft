package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class StaffPermissionsResponse {
    private StaffData data;
    private int status;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StaffData {
        private List<StaffMember> staff;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StaffMember {
        private String minecraftUuid, minecraftUsername, staffUsername, staffId, staffRole, email;
        private List<String> permissions;
    }
}
