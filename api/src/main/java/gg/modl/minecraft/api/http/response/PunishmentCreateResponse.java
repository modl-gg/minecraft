package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PunishmentCreateResponse {
    private String message, punishmentId;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}