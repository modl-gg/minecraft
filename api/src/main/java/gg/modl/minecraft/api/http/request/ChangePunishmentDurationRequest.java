package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePunishmentDurationRequest {
    private String punishmentId;
    private String issuerName;
    private Long newDuration;
}
