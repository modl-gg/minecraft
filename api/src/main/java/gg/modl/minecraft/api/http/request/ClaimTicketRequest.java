package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimTicketRequest {
    private String ticketId;
    private String playerUuid;
    private String playerName;
}
