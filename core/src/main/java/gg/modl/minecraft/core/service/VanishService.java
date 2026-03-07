package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;

import java.util.UUID;

public class VanishService {
    private final CachedProfileRegistry registry;

    public VanishService(CachedProfileRegistry registry) {
        this.registry = registry;
    }

    public void vanish(UUID uuid) {
        CachedProfile profile = registry.getProfile(uuid);
        if (profile != null) profile.setVanished(true);
    }

    public void unvanish(UUID uuid) {
        CachedProfile profile = registry.getProfile(uuid);
        if (profile != null) profile.setVanished(false);
    }

    public boolean isVanished(UUID uuid) {
        CachedProfile profile = registry.getProfile(uuid);
        return profile != null && profile.isVanished();
    }

    /** @return true if now vanished, false if now visible */
    public boolean toggle(UUID uuid) {
        CachedProfile profile = registry.getProfile(uuid);
        if (profile == null) return false;
        boolean newState = !profile.isVanished();
        profile.setVanished(newState);
        return newState;
    }
}
