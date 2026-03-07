package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.impl.cache.PlayerProfile;
import gg.modl.minecraft.core.impl.cache.PlayerProfileRegistry;

import java.util.UUID;

public class FreezeService {
    private final PlayerProfileRegistry registry;

    public FreezeService(PlayerProfileRegistry registry) {
        this.registry = registry;
    }

    public void freeze(UUID target, UUID staff) {
        PlayerProfile profile = registry.getProfile(target);
        if (profile != null) profile.setFrozenByStaff(staff);
    }

    public void unfreeze(UUID target) {
        PlayerProfile profile = registry.getProfile(target);
        if (profile != null) profile.setFrozenByStaff(null);
    }

    public boolean isFrozen(UUID target) {
        PlayerProfile profile = registry.getProfile(target);
        return profile != null && profile.getFrozenByStaff() != null;
    }
}
