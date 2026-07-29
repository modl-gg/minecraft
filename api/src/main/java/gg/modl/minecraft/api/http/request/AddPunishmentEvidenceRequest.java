package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class AddPunishmentEvidenceRequest {
    String punishmentId, issuerName, issuerId, evidenceUrl;
}
