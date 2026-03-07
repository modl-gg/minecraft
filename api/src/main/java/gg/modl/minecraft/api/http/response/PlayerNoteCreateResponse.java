package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class PlayerNoteCreateResponse {
    private final String message;
    private final int status;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}