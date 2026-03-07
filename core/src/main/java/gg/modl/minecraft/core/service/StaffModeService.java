package gg.modl.minecraft.core.service;

import gg.modl.minecraft.core.cache.CachedProfile;
import gg.modl.minecraft.core.cache.CachedProfileRegistry;

import java.util.UUID;

public class StaffModeService {
    public enum StaffModeState {
        OFF,
        STAFF,
        TARGETING
    }

    private final CachedProfileRegistry registry;

    public StaffModeService(CachedProfileRegistry registry) {
        this.registry = registry;
    }

    public void enable(UUID staff) {
        CachedProfile profile = registry.getProfile(staff);
        if (profile != null) profile.setStaffModeState(StaffModeState.STAFF);
    }

    public void disable(UUID staff) {
        CachedProfile profile = registry.getProfile(staff);
        if (profile != null) profile.setStaffModeState(StaffModeState.OFF);
    }

    public boolean isInStaffMode(UUID staff) {
        CachedProfile profile = registry.getProfile(staff);
        if (profile == null) return false;
        return profile.getStaffModeState() != StaffModeState.OFF;
    }

    public StaffModeState getState(UUID staff) {
        CachedProfile profile = registry.getProfile(staff);
        return profile != null ? profile.getStaffModeState() : StaffModeState.OFF;
    }

    public void setTarget(UUID staff, UUID target) {
        CachedProfile profile = registry.getProfile(staff);
        if (profile != null) {
            profile.setTargetPlayerUuid(target);
            profile.setStaffModeState(StaffModeState.TARGETING);
        }
    }

    public void clearTarget(UUID staff) {
        CachedProfile profile = registry.getProfile(staff);
        if (profile != null && profile.getStaffModeState() == StaffModeState.TARGETING) {
            profile.setTargetPlayerUuid(null);
            profile.setStaffModeState(StaffModeState.STAFF);
        }
    }
}
