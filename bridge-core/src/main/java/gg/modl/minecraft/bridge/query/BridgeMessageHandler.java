package gg.modl.minecraft.bridge.query;

public interface BridgeMessageHandler {

    void onFreeze(String targetUuid, String staffUuid);

    void onUnfreeze(String targetUuid);

    void onStaffModeEnter(String staffUuid, String staffName);

    void onStaffModeExit(String staffUuid, String staffName);

    void onVanishEnter(String staffUuid, String staffName);

    void onVanishExit(String staffUuid, String staffName);

    void onTargetRequest(String staffUuid, String targetUuid);

    void onStatWipe(String username, String uuid, String punishmentId);

    void onCaptureReplay(String targetUuid, String targetName);

    void onPanelUrl(String panelUrl);
}
