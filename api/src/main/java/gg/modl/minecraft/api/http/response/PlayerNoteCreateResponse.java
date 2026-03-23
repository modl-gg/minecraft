package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlayerNoteCreateResponse {
    private String message;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}