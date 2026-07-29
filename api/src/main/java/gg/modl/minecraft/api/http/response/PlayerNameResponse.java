package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class PlayerNameResponse extends StatusResponse {
    private String message;
    private Account player;
    private int status;
}
