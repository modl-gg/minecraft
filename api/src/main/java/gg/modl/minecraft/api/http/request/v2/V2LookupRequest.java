package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 API lookup request matching backend's LookupRequest record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2LookupRequest {
    private String query;
    private boolean queryMojang;
}
