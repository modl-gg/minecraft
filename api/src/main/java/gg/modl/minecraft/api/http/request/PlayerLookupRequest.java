package gg.modl.minecraft.api.http.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerLookupRequest {
    private String query; // Can be username or UUID
}