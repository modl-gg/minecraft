package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value @AllArgsConstructor
public class PlayerLookupRequest {
    String query;
    boolean queryMojang;

    public PlayerLookupRequest(String query) {
        this(query, false);
    }
}
