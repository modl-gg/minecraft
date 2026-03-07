package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class TogglePunishmentOptionRequest {
    private String punishmentId, issuerName, option;
    private boolean enabled;
}
