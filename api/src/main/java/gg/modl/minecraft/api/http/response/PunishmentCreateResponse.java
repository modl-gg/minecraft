package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class PunishmentCreateResponse {
    private final int status;
    private final String message;
    private final String punishmentId;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}