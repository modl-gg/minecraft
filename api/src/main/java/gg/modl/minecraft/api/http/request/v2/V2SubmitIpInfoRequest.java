package gg.modl.minecraft.api.http.request.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class V2SubmitIpInfoRequest {
    private String minecraftUUID;
    private String ip;
    private String country;
    private String region;
    private String asn;
    private boolean proxy;
    private boolean hosting;
}
