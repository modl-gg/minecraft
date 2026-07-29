package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class ClaimTicketRequest {
    String ticketId, playerUuid, playerName;
}
