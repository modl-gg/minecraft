package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter @NoArgsConstructor @AllArgsConstructor
public class CreateTicketResponse {
    private @Nullable String ticketId, message;
    private @Nullable TicketInfo ticket;
    private boolean success;

    @Getter
    public static class TicketInfo {
        private @NotNull String id, type;
        private @Nullable String subject, status, created;
    }
}
