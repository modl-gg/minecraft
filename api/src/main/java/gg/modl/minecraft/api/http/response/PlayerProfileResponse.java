package gg.modl.minecraft.api.http.response;

import gg.modl.minecraft.api.Account;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class PlayerProfileResponse {
    private int status;
    @NotNull
    private Account profile;
}