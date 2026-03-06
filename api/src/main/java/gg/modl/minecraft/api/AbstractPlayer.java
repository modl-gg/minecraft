package gg.modl.minecraft.api;

import lombok.Getter;

import java.util.UUID;

public record AbstractPlayer(UUID uuid, String username, boolean online, String ipAddress) {

    public AbstractPlayer(UUID uuid, String username, boolean online) {
        this(uuid, username, online, null);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return username;
    }

    public boolean isOnline() {
        return online;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
