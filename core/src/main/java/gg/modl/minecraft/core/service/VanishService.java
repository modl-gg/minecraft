package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages vanish state per player.
 */
public class VanishService {

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public void vanish(UUID uuid) {
        vanished.add(uuid);
    }

    public void unvanish(UUID uuid) {
        vanished.remove(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean toggle(UUID uuid) {
        if (vanished.contains(uuid)) {
            vanished.remove(uuid);
            return false; // now visible
        } else {
            vanished.add(uuid);
            return true; // now vanished
        }
    }

    public Set<UUID> getVanishedPlayers() {
        return Collections.unmodifiableSet(vanished);
    }

    public void removePlayer(UUID uuid) {
        vanished.remove(uuid);
    }
}
