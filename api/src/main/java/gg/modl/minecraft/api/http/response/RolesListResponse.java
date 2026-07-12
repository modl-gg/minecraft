package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class RolesListResponse extends StatusResponse {
    private List<RoleEntry> roles;
    private int status;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class RoleEntry {
        private String id, name, description;
        private List<String> permissions;
        private boolean isDefault;
        private int order;
    }
}
