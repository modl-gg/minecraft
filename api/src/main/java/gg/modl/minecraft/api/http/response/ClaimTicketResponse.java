package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ClaimTicketResponse {
    private String message, ticketId, subject;
    private int status;
    private boolean success;

    public boolean isSuccess() {
        return success && status >= 200 && status < 300;
    }
}
