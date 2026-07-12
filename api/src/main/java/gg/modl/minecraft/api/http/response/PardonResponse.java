package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PardonResponse {
    private String message;
    private int status, pardonedCount;
    private boolean success;

    public boolean hasPardoned() {
        return success && pardonedCount > 0;
    }
}
