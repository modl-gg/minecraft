package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PardonResponse {
    private String message;
    private int status, pardonedCount;
    private boolean success;

    public boolean hasPardoned() {
        return success && pardonedCount > 0;
    }
}
