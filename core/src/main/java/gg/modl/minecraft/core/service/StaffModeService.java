package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.impl.cache.PlayerProfile;
import gg.modl.minecraft.core.impl.cache.PlayerProfileRegistry;

import java.util.UUID;

public class StaffModeService {
    public enum StaffModeState {
        OFF,
        STAFF,
        TARGETING
    }

    private final PlayerProfileRegistry registry;

    public StaffModeService(PlayerProfileRegistry registry) {
        this.registry = registry;
    }

    public void enable(UUID staff) {
        PlayerProfile profile = registry.getProfile(staff);
        if (profile != null) profile.setStaffModeState(StaffModeState.STAFF);
    }

    public void disable(UUID staff) {
        PlayerProfile profile = registry.getProfile(staff);
        if (profile != null) profile.setStaffModeState(StaffModeState.OFF);
    }

    public boolean isInStaffMode(UUID staff) {
        PlayerProfile profile = registry.getProfile(staff);
        if (profile == null) return false;
        return profile.getStaffModeState() != StaffModeState.OFF;
    }

    public StaffModeState getState(UUID staff) {
        PlayerProfile profile = registry.getProfile(staff);
        return profile != null ? profile.getStaffModeState() : StaffModeState.OFF;
    }

    public void setTarget(UUID staff, UUID target) {
        PlayerProfile profile = registry.getProfile(staff);
        if (profile != null) {
            profile.setTargetPlayerUuid(target);
            profile.setStaffModeState(StaffModeState.TARGETING);
        }
    }

    public void clearTarget(UUID staff) {
        PlayerProfile profile = registry.getProfile(staff);
        if (profile != null && profile.getStaffModeState() == StaffModeState.TARGETING) {
            profile.setTargetPlayerUuid(null);
            profile.setStaffModeState(StaffModeState.STAFF);
        }
    }
}
