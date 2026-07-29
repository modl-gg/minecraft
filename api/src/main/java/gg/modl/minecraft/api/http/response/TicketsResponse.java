package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class TicketsResponse extends StatusResponse {
    private List<Ticket> tickets;
    private int status;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Ticket {
        private String id, type, category, subject, status, playerName, playerUuid, priority, firstReplyContent;
        private List<String> assignedTo;
        private Date createdAt, updatedAt;
        private boolean hasStaffResponse, locked;
        private int replyCount;
    }
}
