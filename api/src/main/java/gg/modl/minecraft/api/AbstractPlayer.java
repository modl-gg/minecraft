package gg.modl.minecraft.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data @AllArgsConstructor
public class AbstractPlayer {
    private final UUID uuid;
    private final String username;
    private final String ipAddress;
    private final boolean online;

    public AbstractPlayer(UUID uuid, String username, boolean online) {
        this(uuid, username, null, online);
    }

    public String getName() {
        return username;
    }
}
