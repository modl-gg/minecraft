package gg.modl.minecraft.core.bridge.protocol;

public enum BridgeAction {
    STAFF_MODE_ENTER,
    STAFF_MODE_EXIT,
    VANISH_ENTER,
    VANISH_EXIT,
    FREEZE_PLAYER(Delivery.IMMEDIATE),
    UNFREEZE_PLAYER(Delivery.IMMEDIATE),
    FREEZE_LOGOUT,
    TARGET_REQUEST,
    TARGET_RESPONSE,
    OPEN_STAFF_MENU,
    OPEN_INSPECT_MENU,
    PROXY_CMD,
    CREATE_REPORT,
    CAPTURE_REPLAY(Delivery.IMMEDIATE),
    CAPTURE_REPLAY_RESPONSE,
    STAT_WIPE,
    PANEL_URL,
    BRIDGE_HELLO,
    CONNECT_SERVER;

    private final Delivery delivery;

    BridgeAction() {
        this(Delivery.QUEUE_UNTIL_CONNECTED);
    }

    BridgeAction(Delivery delivery) {
        this.delivery = delivery;
    }

    public String wire() {
        return name();
    }

    public boolean isQueueable() {
        return delivery == Delivery.QUEUE_UNTIL_CONNECTED;
    }

    public static BridgeAction fromWire(String wire) {
        for (BridgeAction action : values()) {
            if (action.name().equals(wire)) {
                return action;
            }
        }
        return null;
    }

    public enum Delivery {
        QUEUE_UNTIL_CONNECTED,
        IMMEDIATE
    }
}
