package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffListResponse {
    private int status;
    private List<StaffEntry> staff;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffEntry {
        private String id;
        private String username;
        private String email;
        private String role;
        private String minecraftUuid;
        private String minecraftUsername;
        private List<String> permissions;
    }
}
