package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeService {
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();

    public void freeze(UUID target, UUID staff) {
        frozenPlayers.put(target, staff);
    }

    public void unfreeze(UUID target) {
        frozenPlayers.remove(target);
    }

    public boolean isFrozen(UUID target) {
        return frozenPlayers.containsKey(target);
    }

    public UUID getFreezer(UUID target) {
        return frozenPlayers.get(target);
    }

    public Set<UUID> getFrozenPlayers() {
        return Collections.unmodifiableSet(frozenPlayers.keySet());
    }

    /** Alias for {@link #unfreeze} — used on player disconnect. */
    public void removePlayer(UUID uuid) {
        unfreeze(uuid);
    }
}
