package gg.modl.minecraft.api;

import java.util.UUID;

public record AbstractPlayer(UUID uuid, String username, boolean online, String ipAddress) {
    
    // Constructor for backward compatibility (without IP address)
    public AbstractPlayer(UUID uuid, String username, boolean online) {
        this(uuid, username, online, null);
    }
    
    // Convenience getters for record fields
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
