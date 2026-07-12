package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class TogglePunishmentOptionRequest {
    String punishmentId, issuerName, issuerId, option;
    boolean enabled;
}
