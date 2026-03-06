package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffModeService {
    public enum StaffModeState {
        OFF,
        STAFF,
        TARGETING
    }

    private final Map<UUID, StaffModeState> staffModes = new ConcurrentHashMap<>();

    public void enable(UUID staff) {
        staffModes.put(staff, StaffModeState.STAFF);
    }

    public void disable(UUID staff) {
        staffModes.remove(staff);
    }

    public boolean isInStaffMode(UUID staff) {
        StaffModeState state = staffModes.get(staff);
        return state != null && state != StaffModeState.OFF;
    }

    public StaffModeState getState(UUID staff) {
        return staffModes.getOrDefault(staff, StaffModeState.OFF);
    }

    public void setTarget(UUID staff, UUID target) {
        staffModes.put(staff, StaffModeState.TARGETING);
    }

    public void clearTarget(UUID staff) {
        if (staffModes.get(staff) == StaffModeState.TARGETING) staffModes.put(staff, StaffModeState.STAFF);
    }

    public void removePlayer(UUID uuid) {
        staffModes.remove(uuid);
    }
}
