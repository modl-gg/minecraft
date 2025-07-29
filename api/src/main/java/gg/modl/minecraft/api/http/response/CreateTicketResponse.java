package gg.modl.minecraft.api.http.response;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
public class CreateTicketResponse {
    private boolean success;
    @Nullable
    private String ticketId;
    @Nullable
    private String message;
    @Nullable
    private TicketInfo ticket;
    
    @Data
    public static class TicketInfo {
        @NotNull
        private String id;
        @NotNull
        private String type;
        @Nullable
        private String subject;
        @Nullable
        private String status;
        @Nullable
        private String created;
    }
}