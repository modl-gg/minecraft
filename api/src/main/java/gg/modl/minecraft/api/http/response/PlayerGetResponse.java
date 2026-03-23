package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class PlayerGetResponse {
    private String message;
    private Account player;
    private int status;

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}