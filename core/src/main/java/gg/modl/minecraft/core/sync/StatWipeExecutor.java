package gg.modl.minecraft.core.sync;

/**
 * Clears player game data (stats, inventory, progress) as part of a punishment.
 * Invoked by the sync engine when a pending stat wipe is received from the backend.
 */
public interface StatWipeExecutor {
    void executeStatWipe(String username, String uuid, String punishmentId, StatWipeCallback callback);

    interface StatWipeCallback {
        void onComplete(boolean success, String serverName);
    }
}
