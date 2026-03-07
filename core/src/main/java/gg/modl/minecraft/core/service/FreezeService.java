package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;

import java.util.UUID;

public class FreezeService {
    private final CachedProfileRegistry registry;

    public FreezeService(CachedProfileRegistry registry) {
        this.registry = registry;
    }

    public void freeze(UUID target, UUID staff) {
        CachedProfile profile = registry.getProfile(target);
        if (profile != null) profile.setFrozenByStaff(staff);
    }

    public void unfreeze(UUID target) {
        CachedProfile profile = registry.getProfile(target);
        if (profile != null) profile.setFrozenByStaff(null);
    }

    public boolean isFrozen(UUID target) {
        CachedProfile profile = registry.getProfile(target);
        return profile != null && profile.getFrozenByStaff() != null;
    }
}
