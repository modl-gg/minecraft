package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PunishmentCreateResponse extends StatusResponse {
    private String message, punishmentId;
    private int status;
}
