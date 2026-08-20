package gg.modl.minecraft.core.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFrozenPlayerStore implements FrozenPlayerStore {
    private final Map<UUID, UUID> frozenPlayerToStaff = new ConcurrentHashMap<>();

    @Override
    public void freeze(UUID target, UUID staff) {
        frozenPlayerToStaff.put(target, staff);
    }

    @Override
    public void unfreeze(UUID target) {
        frozenPlayerToStaff.remove(target);
    }

    @Override
    public boolean isFrozen(UUID target) {
        return frozenPlayerToStaff.containsKey(target);
    }

    @Override
    public boolean releaseOnDisconnect(UUID target) {
        return frozenPlayerToStaff.remove(target) != null;
    }
}
