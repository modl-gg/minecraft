package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class ModifyPunishmentTicketsRequest {
    private String punishmentId, issuerName, issuerId;
    private List<String> addTicketIds, removeTicketIds;
    private boolean modifyAssociatedTickets;
}
