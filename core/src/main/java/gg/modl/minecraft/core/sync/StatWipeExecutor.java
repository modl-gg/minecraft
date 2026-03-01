package gg.modl.minecraft.core.sync;

public interface StatWipeExecutor {

    void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback);

    interface StatWipeCallback {
        void onComplete(boolean success, String serverName);
    }
}
