package gg.modl.minecraft.core.cache;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CachedProfileRegistry {
    private final Map<UUID, CachedProfile> profiles = new ConcurrentHashMap<>();

    public CachedProfile createProfile(UUID uuid) {
        CachedProfile profile = new CachedProfile(uuid);
        profiles.put(uuid, profile);
        return profile;
    }

    public @Nullable CachedProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void destroyProfile(UUID uuid) {
        profiles.remove(uuid);
    }

    public boolean hasProfile(UUID uuid) {
        return profiles.containsKey(uuid);
    }

    public Set<UUID> getOnlinePlayers() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    public Collection<CachedProfile> getAllProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public Set<UUID> getInterceptors() {
        Set<UUID> result = new HashSet<>();
        for (CachedProfile profile : profiles.values()) {
            if (profile.isInterceptingNetworkChat()) result.add(profile.getUuid());
        }
        return Collections.unmodifiableSet(result);
    }

    public void clear() {
        profiles.clear();
    }
}
