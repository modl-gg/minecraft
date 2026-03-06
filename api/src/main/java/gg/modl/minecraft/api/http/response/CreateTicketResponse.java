package gg.modl.minecraft.api.http.response;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class CreateTicketResponse {
    private boolean success;
    private @Nullable String ticketId;
    private @Nullable String message;
    private @Nullable TicketInfo ticket;
    
    @Data
    public static class TicketInfo {
        private @NotNull String id;
        private @NotNull String type;
        private @Nullable String subject;
        private @Nullable String status;
        private @Nullable String created;
    }
}