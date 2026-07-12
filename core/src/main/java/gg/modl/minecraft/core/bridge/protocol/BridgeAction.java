package gg.modl.minecraft.core.bridge.protocol;

public enum BridgeAction {
    STAFF_MODE_ENTER,
    STAFF_MODE_EXIT,
    VANISH_ENTER,
    VANISH_EXIT,
    FREEZE_PLAYER,
    UNFREEZE_PLAYER,
    FREEZE_LOGOUT,
    TARGET_REQUEST,
    TARGET_RESPONSE,
    OPEN_STAFF_MENU,
    OPEN_INSPECT_MENU,
    PROXY_CMD,
    CREATE_REPORT,
    CAPTURE_REPLAY,
    CAPTURE_REPLAY_RESPONSE,
    STAT_WIPE,
    PANEL_URL,
    BRIDGE_HELLO,
    CONNECT_SERVER;

    public String wire() {
        return name();
    }

    public static BridgeAction fromWire(String wire) {
        for (BridgeAction action : values()) {
            if (action.name().equals(wire)) {
                return action;
            }
        }
        return null;
    }
}
