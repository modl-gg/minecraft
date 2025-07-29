package gg.modl.minecraft.api.http.request;

import lombok.Data;

@Data
public class PlayerGetRequest {
    private final String minecraftUuid;
}