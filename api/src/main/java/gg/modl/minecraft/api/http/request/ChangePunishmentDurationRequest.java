package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class ChangePunishmentDurationRequest {
    String punishmentId, issuerName, issuerId;
    Long newDuration;
}
