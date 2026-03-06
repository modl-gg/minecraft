package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;

@Data
public class PlayerNameResponse {
    private final int status;
    private final String message;
    private final Account player;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}