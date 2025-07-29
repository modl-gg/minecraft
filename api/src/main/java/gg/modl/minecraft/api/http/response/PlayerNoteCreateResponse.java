package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class PlayerNoteCreateResponse {
    private final int status;
    private final String message;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}