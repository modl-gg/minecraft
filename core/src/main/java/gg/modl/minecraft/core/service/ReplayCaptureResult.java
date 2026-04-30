package gg.modl.minecraft.core.service;

public final class ReplayCaptureResult {
    private static final ReplayCaptureResult FABRIC_DISABLED =
            new ReplayCaptureResult(ReplayCaptureStatus.FABRIC_DISABLED, null);
    private static final ReplayCaptureResult NO_BRIDGE_CONNECTED =
            new ReplayCaptureResult(ReplayCaptureStatus.NO_BRIDGE_CONNECTED, null);
    private static final ReplayCaptureResult NO_ACTIVE_RECORDING =
            new ReplayCaptureResult(ReplayCaptureStatus.NO_ACTIVE_RECORDING, null);
    private static final ReplayCaptureResult NOT_LOCAL =
            new ReplayCaptureResult(ReplayCaptureStatus.NOT_LOCAL, null);
    private static final ReplayCaptureResult ERROR =
            new ReplayCaptureResult(ReplayCaptureStatus.ERROR, null);

    private final ReplayCaptureStatus status;
    private final String replayId;

    private ReplayCaptureResult(ReplayCaptureStatus status, String replayId) {
        this.status = status;
        this.replayId = replayId;
    }

    public static ReplayCaptureResult ok(String replayId) {
        if (replayId == null || replayId.isEmpty()) {
            return error();
        }
        return new ReplayCaptureResult(ReplayCaptureStatus.OK, replayId);
    }

    public static ReplayCaptureResult fabricDisabled() {
        return FABRIC_DISABLED;
    }

    public static ReplayCaptureResult noBridgeConnected() {
        return NO_BRIDGE_CONNECTED;
    }

    public static ReplayCaptureResult noActiveRecording() {
        return NO_ACTIVE_RECORDING;
    }

    public static ReplayCaptureResult notLocal() {
        return NOT_LOCAL;
    }

    public static ReplayCaptureResult error() {
        return ERROR;
    }

    public static ReplayCaptureResult of(ReplayCaptureStatus status, String replayId) {
        if (status == ReplayCaptureStatus.OK) {
            return ok(replayId);
        }
        if (status == ReplayCaptureStatus.FABRIC_DISABLED) return fabricDisabled();
        if (status == ReplayCaptureStatus.NO_BRIDGE_CONNECTED) return noBridgeConnected();
        if (status == ReplayCaptureStatus.NOT_LOCAL) return notLocal();
        if (status == ReplayCaptureStatus.ERROR) return error();
        return noActiveRecording();
    }

    public ReplayCaptureStatus getStatus() {
        return status;
    }

    public String getReplayId() {
        return replayId;
    }
}
