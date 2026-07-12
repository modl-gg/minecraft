package gg.modl.minecraft.api.http.request;

import lombok.Value;

@Value
public class AddPunishmentNoteRequest {
    String punishmentId, issuerName, issuerId, note;
}
