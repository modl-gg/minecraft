package gg.modl.minecraft.core;

public interface Scheduler {
    void runOnMainThread(Runnable task);

    default void runOnGameThread(Runnable task) {
        task.run();
    }
}
