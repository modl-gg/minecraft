package gg.modl.minecraft.core;

import java.util.UUID;

@FunctionalInterface
public interface StaffAudience {
    boolean includes(UUID uuid);
}
