package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffModeService {
    public enum StaffModeState {
        OFF,
        STAFF,
        TARGETING
    }

    private final Map<UUID, StaffModeState> staffModes = new ConcurrentHashMap<>();
    /** staff UUID -> target UUID */
    private final Map<UUID, UUID> targetMap = new ConcurrentHashMap<>();

    public void enable(UUID staff) {
        staffModes.put(staff, StaffModeState.STAFF);
    }

    public void disable(UUID staff) {
        staffModes.remove(staff);
        targetMap.remove(staff);
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
        targetMap.put(staff, target);
    }

    public void clearTarget(UUID staff) {
        targetMap.remove(staff);
        if (staffModes.get(staff) == StaffModeState.TARGETING) staffModes.put(staff, StaffModeState.STAFF);
    }

    public UUID getTarget(UUID staff) {
        return targetMap.get(staff);
    }

    public Set<UUID> getStaffInMode() {
        return Collections.unmodifiableSet(staffModes.keySet());
    }

    public void removePlayer(UUID uuid) {
        staffModes.remove(uuid);
        targetMap.remove(uuid);
    }
}
