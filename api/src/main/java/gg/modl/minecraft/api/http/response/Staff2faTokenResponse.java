package gg.modl.minecraft.api.http.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class Staff2faTokenResponse {
    private String token;
    private String verifyUrl;
}
