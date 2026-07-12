package gg.modl.minecraft.core.integration.mojang;

import lombok.Value;

import java.util.UUID;

@Value
public class WebPlayer {
    private static final WebPlayer INVALID = new WebPlayer(null, null, null, null, false);

    String name;
    UUID uuid;
    String skin;
    String textureValue;
    boolean valid;

    public static WebPlayer invalid() {
        return INVALID;
    }
}
