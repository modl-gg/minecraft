package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class StaffListResponse extends StatusResponse {
    private List<StaffEntry> staff;
    private int status;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StaffEntry {
        private String id, username, email, role, minecraftUuid, minecraftUsername, lastServer;
        private List<String> permissions;
        private Date lastSeen;
        private Long totalPlaytimeMs;
        private int punishmentsIssuedCount;
    }
}
