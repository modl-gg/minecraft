package gg.modl.minecraft.api.http.response;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class CreateTicketResponse {
    private @Nullable String ticketId, message;
    private @Nullable TicketInfo ticket;
    private boolean success;

    @Data
    public static class TicketInfo {
        private @NotNull String id, type;
        private @Nullable String subject, status, created;
    }
}