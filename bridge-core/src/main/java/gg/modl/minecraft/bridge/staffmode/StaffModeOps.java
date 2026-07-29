package gg.modl.minecraft.bridge.staffmode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StaffModeOps {
    boolean isOnline(UUID uuid);

    String playerName(UUID uuid);

    Set<UUID> onlinePlayerUuids();

    int onlinePlayerCount();

    int maxPlayerCount();

    int playerPing(UUID uuid);

    double playerHealth(UUID uuid);

    void clearInventory(UUID uuid);

    void clearArmor(UUID uuid);

    void setGameMode(UUID uuid, StaffGameMode mode);

    void setHotbarSlot(UUID uuid, int slot, String materialId, String displayName, List<String> lore);

    boolean hasSnapshot(UUID uuid);

    void saveSnapshot(UUID uuid);

    void restoreSnapshot(UUID uuid);

    void discardSnapshot(UUID uuid);

    void clearSnapshots();

    void hidePlayer(UUID viewerUuid, UUID hiddenUuid);

    void showPlayer(UUID viewerUuid, UUID shownUuid);

    void teleportToPlayer(UUID uuid, UUID targetUuid);

    void createScoreboard(UUID uuid, ScoreboardContent content);

    void updateScoreboard(UUID uuid, ScoreboardContent content);

    void removeScoreboard(UUID uuid);

    void discardScoreboard(UUID uuid);

    void clearScoreboards();

    void openTargetInventory(UUID viewerUuid, UUID targetUuid);

    void runPlayerCommand(UUID uuid, String command);

    void sendMessage(UUID uuid, String message);
}
