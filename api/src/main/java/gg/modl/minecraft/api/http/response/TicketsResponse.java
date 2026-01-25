package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketsResponse {
    private int status;
    private List<Ticket> tickets;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ticket {
        private String id;
        private String type;
        private String category;
        private String subject;
        private String status;
        private String playerName;
        private String playerUuid;
        private String priority;
        private String assignedTo;
        private Date createdAt;
        private Date updatedAt;
        private boolean hasStaffResponse;
        private int replyCount;
        private boolean locked;
    }
}
