package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolesListResponse {
    private int status;
    private List<RoleEntry> roles;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleEntry {
        private String id;
        private String name;
        private String description;
        private List<String> permissions;
        private boolean isDefault;
        private int order;
    }
}
