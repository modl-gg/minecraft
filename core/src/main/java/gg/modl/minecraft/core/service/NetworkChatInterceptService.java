package gg.modl.minecraft.core.service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkChatInterceptService {
    private final Set<UUID> intercepting = ConcurrentHashMap.newKeySet();

    /** @return true if interception is now enabled, false if disabled */
    public boolean toggle(UUID uuid) {
        if (!intercepting.add(uuid)) {
            intercepting.remove(uuid);
            return false;
        }
        return true;
    }

    public boolean isIntercepting(UUID uuid) {
        return intercepting.contains(uuid);
    }

    public Set<UUID> getInterceptors() {
        return Collections.unmodifiableSet(intercepting);
    }

    public void removePlayer(UUID uuid) {
        intercepting.remove(uuid);
    }
}
