package gg.modl.minecraft.bridge.reporter.hook;

public interface AntiCheatHook {

    String getName();
    boolean isAvailable();
    void register();
    void unregister();
}
