package gg.modl.minecraft.core.service;

import java.util.UUID;

public interface FrozenPlayerStore {
    void freeze(UUID target, UUID staff);

    void unfreeze(UUID target);

    boolean isFrozen(UUID target);

    boolean releaseOnDisconnect(UUID target);
}
