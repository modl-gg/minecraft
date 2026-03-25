package gg.modl.minecraft.core.query;

public interface BridgeBroadcaster {
    void sendToAllBridges(String action, String... args);
    boolean hasConnectedClients();
}
