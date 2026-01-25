package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TogglePunishmentOptionRequest {
    private String punishmentId;
    private String issuerName;
    private String option; // "ALT_BLOCKING" or "STAT_WIPE"
    private boolean enabled;
}
