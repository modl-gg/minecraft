package gg.modl.minecraft.api;

import lombok.Getter;

import java.util.UUID;

public record AbstractPlayer(UUID uuid, String username, String ipAddress, boolean online) {

    public AbstractPlayer(UUID uuid, String username, boolean online) {
        this(uuid, username, null, online);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isOnline() {
        return online;
    }
}
