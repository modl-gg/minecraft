package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlayerLookupRequest {
    private String query;
    private boolean queryMojang;

    public PlayerLookupRequest(String query) {
        this(query, false);
    }
}
