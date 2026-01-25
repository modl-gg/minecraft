package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportsResponse {
    private int status;
    private List<Report> reports;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Report {
        private String id;
        private String type;
        private String reporterName;
        private String reporterUuid;
        private String reportedPlayerUuid;
        private String reportedPlayerName;
        private String subject;
        private String status;
        private String priority;
        private Date createdAt;
        private String assignedTo;
    }
}
