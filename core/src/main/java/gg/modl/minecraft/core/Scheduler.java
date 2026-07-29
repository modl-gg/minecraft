package gg.modl.minecraft.core;

public interface Scheduler {
    void runOnMainThread(Runnable task);
}
