package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class ClaimTicketResponse extends StatusResponse {
    private String message, ticketId, subject;
    private int status;
    private boolean success;

    @Override
    public boolean isSuccess() {
        return success && super.isSuccess();
    }
}
