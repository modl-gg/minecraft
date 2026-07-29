package gg.modl.minecraft.core.boot;

public final class SyncPollingRate {
    public static final String CONFIG_KEY = "sync.polling_rate";
    public static final int DEFAULT_SECONDS = 2;
    private static final int MIN_SECONDS = 1;

    private SyncPollingRate() {
    }

    public static int clamp(int seconds) {
        return Math.max(MIN_SECONDS, seconds);
    }
}
