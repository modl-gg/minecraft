package gg.modl.minecraft.core.service;

import java.util.Objects;
import java.util.UUID;

public class FreezeService implements FrozenPlayerStore {
    private volatile FrozenPlayerStore store = new InMemoryFrozenPlayerStore();

    public void bindStore(FrozenPlayerStore enforcingStore) {
        this.store = Objects.requireNonNull(enforcingStore, "enforcingStore");
    }

    @Override
    public void freeze(UUID target, UUID staff) {
        store.freeze(target, staff);
    }

    @Override
    public void unfreeze(UUID target) {
        store.unfreeze(target);
    }

    @Override
    public boolean isFrozen(UUID target) {
        return store.isFrozen(target);
    }

    @Override
    public boolean releaseOnDisconnect(UUID target) {
        return store.releaseOnDisconnect(target);
    }
}
