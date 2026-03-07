package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class EvidenceUploadTokenResponse {
    private String token;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300 && token != null;
    }
}
