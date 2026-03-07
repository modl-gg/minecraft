package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class TicketsResponse {
    private List<Ticket> tickets;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Ticket {
        private String id, type, category, subject, status, playerName, playerUuid, priority, firstReplyContent;
        private List<String> assignedTo;
        private Date createdAt, updatedAt;
        private boolean hasStaffResponse, locked;
        private int replyCount;
    }
}
