package gg.modl.minecraft.api.http.request;

import lombok.Builder;
import lombok.Value;

@Value @Builder
public class PlayerLookupRequest {
    String query;
    boolean queryMojang;
}
