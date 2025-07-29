package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;

@Data
public class PlayerNameResponse {
    private final int status;
    private final String message;
    private final Account player; // This would be a proper Player DTO in a real implementation
    
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}