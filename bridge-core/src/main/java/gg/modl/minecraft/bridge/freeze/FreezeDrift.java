package gg.modl.minecraft.bridge.freeze;

public final class FreezeDrift {
    private static final double MAX_DRIFT_SQUARED = 0.01;

    private FreezeDrift() {}

    public static boolean exceeded(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz > MAX_DRIFT_SQUARED;
    }
}
