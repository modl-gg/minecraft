package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class ReportsResponse {
    private List<Report> reports;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Report {
        private String id, type, category, reporterName, reporterUuid, reportedPlayerUuid, reportedPlayerName,
                subject, content, status, priority;
        private Date createdAt;
        private List<String> assignedTo;
        private List<Object> chatMessages;
    }
}
