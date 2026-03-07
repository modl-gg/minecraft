package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;

import java.util.Set;
import java.util.UUID;

public class NetworkChatInterceptService {
    private final CachedProfileRegistry registry;

    public NetworkChatInterceptService(CachedProfileRegistry registry) {
        this.registry = registry;
    }

    /** @return true if interception is now enabled, false if disabled */
    public boolean toggle(UUID uuid) {
        CachedProfile profile = registry.getProfile(uuid);
        if (profile == null) return false;
        boolean newState = !profile.isInterceptingNetworkChat();
        profile.setInterceptingNetworkChat(newState);
        return newState;
    }

    public Set<UUID> getInterceptors() {
        return registry.getInterceptors();
    }
}
