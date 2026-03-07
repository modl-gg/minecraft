package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;

@Data
public class PlayerNameResponse {
    private final String message;
    private final Account player;
    private final int status;
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}