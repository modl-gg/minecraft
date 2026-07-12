package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.UUID;

@Value @AllArgsConstructor
public class AbstractPlayer {
    UUID uuid;
    String username;
    String ipAddress;
    boolean online;

    public AbstractPlayer(UUID uuid, String username, boolean online) {
        this(uuid, username, null, online);
    }

    public String getName() {
        return username;
    }
}
