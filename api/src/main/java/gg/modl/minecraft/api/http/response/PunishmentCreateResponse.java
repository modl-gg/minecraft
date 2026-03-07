package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class PunishmentCreateResponse {
    private final String message, punishmentId;
    private final int status;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}