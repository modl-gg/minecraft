package gg.modl.minecraft.core.support;

import gg.modl.minecraft.core.query.BridgeBroadcaster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingBridgeBroadcaster implements BridgeBroadcaster {
    private final boolean connected;
    private final int dispatchCount;
    private final List<String> sentActions = new CopyOnWriteArrayList<>();

    public RecordingBridgeBroadcaster(boolean connected) {
        this(connected, connected ? 1 : 0);
    }

    public RecordingBridgeBroadcaster(boolean connected, int dispatchCount) {
        this.connected = connected;
        this.dispatchCount = dispatchCount;
    }

    @Override
    public int sendToAllBridges(String action, String... args) {
        sentActions.add(action);
        return dispatchCount;
    }

    @Override
    public boolean hasConnectedClients() {
        return connected;
    }

    public List<String> sentActions() {
        return sentActions;
    }
}
