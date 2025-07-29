package gg.modl.minecraft.api.http.request;

import lombok.Data;

@Data
public class PlayerNameRequest {
    private final String minecraftUsername;
}