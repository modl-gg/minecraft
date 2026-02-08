package gg.modl.minecraft.api.http.response;

import lombok.Data;

@Data
public class EvidenceUploadTokenResponse {
    private int status;
    private String token;

    public boolean isSuccess() {
        return status >= 200 && status < 300 && token != null;
    }
}
