package gg.modl.minecraft.spigot.bridge.reporter.hook;

public interface AntiCheatHook {

    String getName();
    boolean isAvailable();
    void register();
    void unregister();
}
