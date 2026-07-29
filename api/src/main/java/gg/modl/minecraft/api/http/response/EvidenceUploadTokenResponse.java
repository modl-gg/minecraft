package gg.modl.minecraft.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class EvidenceUploadTokenResponse extends StatusResponse {
    private String token;
    private int status;

    @Override
    public boolean isSuccess() {
        return super.isSuccess() && token != null;
    }
}
