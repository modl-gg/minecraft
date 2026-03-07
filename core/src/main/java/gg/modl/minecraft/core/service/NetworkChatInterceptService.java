package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.PlayerProfile;
import gg.modl.minecraft.core.cache.PlayerProfileRegistry;

import java.util.Set;
import java.util.UUID;

public class NetworkChatInterceptService {
    private final PlayerProfileRegistry registry;

    public NetworkChatInterceptService(PlayerProfileRegistry registry) {
        this.registry = registry;
    }

    /** @return true if interception is now enabled, false if disabled */
    public boolean toggle(UUID uuid) {
        PlayerProfile profile = registry.getProfile(uuid);
        if (profile == null) return false;
        boolean newState = !profile.isInterceptingNetworkChat();
        profile.setInterceptingNetworkChat(newState);
        return newState;
    }

    public Set<UUID> getInterceptors() {
        return registry.getInterceptors();
    }
}
