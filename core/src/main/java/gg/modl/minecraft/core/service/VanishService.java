package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** @return true if now vanished, false if now visible */
    public boolean toggle(UUID uuid) {
        if (!vanished.add(uuid)) {
            vanished.remove(uuid);
            return false;
        }
        return true;
    }
}
