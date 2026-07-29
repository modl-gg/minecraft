package gg.modl.minecraft.core.integration.mojang;

public final class MojangProfiles {
    private static volatile MojangProfileClient client;

    private MojangProfiles() {}

    public static MojangProfileClient client() {
        MojangProfileClient local = client;
        if (local == null) {
            synchronized (MojangProfiles.class) {
                local = client;
                if (local == null) {
                    local = new MojangProfileClient();
                    client = local;
                }
            }
        }
        return local;
    }

    public static void shutdown() {
        MojangProfileClient local = client;
        if (local != null) local.shutdown();
    }
}
