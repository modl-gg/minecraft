package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PardonResponse {
    private int status;
    private boolean success;
    private int pardonedCount;
    private String message;

    public boolean hasPardoned() {
        return success && pardonedCount > 0;
    }
}
