package gg.modl.minecraft.api.http.request;

import lombok.Value;

import java.util.List;

@Value
public class ModifyPunishmentTicketsRequest {
    String punishmentId, issuerName, issuerId;
    List<String> addTicketIds, removeTicketIds;
    boolean modifyAssociatedTickets;
}
