package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class StaffListResponse {
    private List<StaffEntry> staff;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StaffEntry {
        private String id, username, email, role, minecraftUuid, minecraftUsername, lastServer;
        private List<String> permissions;
        private Date lastSeen;
        private Long totalPlaytimeMs;
        private int punishmentsIssuedCount;
    }
}
