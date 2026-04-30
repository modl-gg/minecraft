package gg.modl.minecraft.core.query;

public interface BridgeBroadcaster {
    int sendToAllBridges(String action, String... args);
    boolean hasConnectedClients();
}
