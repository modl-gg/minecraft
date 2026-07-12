package gg.modl.minecraft.bridge.staffmode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class FakeStaffModeOps implements StaffModeOps {
    final Set<UUID> online = ConcurrentHashMap.newKeySet();
    final Map<UUID, String> names = new HashMap<>();
    final Set<UUID> snapshots = ConcurrentHashMap.newKeySet();
    final Map<UUID, Integer> pings = new HashMap<>();
    final Map<UUID, Double> healths = new HashMap<>();
    int maxPlayers = 20;

    final List<UUID> saved = new ArrayList<>();
    final List<UUID> restored = new ArrayList<>();
    final List<StaffGameMode> gameModes = new ArrayList<>();
    final List<String> hideCalls = new ArrayList<>();
    final List<String> showCalls = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    final List<String> openedInventories = new ArrayList<>();
    final Map<UUID, List<String>> messages = new HashMap<>();
    final Map<UUID, ScoreboardContent> scoreboards = new HashMap<>();
    final List<String> teleports = new ArrayList<>();

    @Override
    public boolean isOnline(UUID uuid) {
        return online.contains(uuid);
    }

    @Override
    public String playerName(UUID uuid) {
        return names.get(uuid);
    }

    @Override
    public Set<UUID> onlinePlayerUuids() {
        return new HashSet<>(online);
    }

    @Override
    public int onlinePlayerCount() {
        return online.size();
    }

    @Override
    public int maxPlayerCount() {
        return maxPlayers;
    }

    @Override
    public int playerPing(UUID uuid) {
        return pings.getOrDefault(uuid, 0);
    }

    @Override
    public double playerHealth(UUID uuid) {
        return healths.getOrDefault(uuid, 20.0);
    }

    @Override
    public void clearInventory(UUID uuid) {
    }

    @Override
    public void clearArmor(UUID uuid) {
    }

    @Override
    public void setGameMode(UUID uuid, StaffGameMode mode) {
        gameModes.add(mode);
    }

    @Override
    public void setHotbarSlot(UUID uuid, int slot, String materialId, String displayName, List<String> lore) {
    }

    @Override
    public boolean hasSnapshot(UUID uuid) {
        return snapshots.contains(uuid);
    }

    @Override
    public void saveSnapshot(UUID uuid) {
        if (snapshots.add(uuid)) {
            saved.add(uuid);
        }
    }

    @Override
    public void restoreSnapshot(UUID uuid) {
        snapshots.remove(uuid);
        restored.add(uuid);
    }

    @Override
    public void discardSnapshot(UUID uuid) {
        snapshots.remove(uuid);
    }

    @Override
    public void clearSnapshots() {
        snapshots.clear();
    }

    @Override
    public void hidePlayer(UUID viewerUuid, UUID hiddenUuid) {
        hideCalls.add(viewerUuid + "<-" + hiddenUuid);
    }

    @Override
    public void showPlayer(UUID viewerUuid, UUID shownUuid) {
        showCalls.add(viewerUuid + "<-" + shownUuid);
    }

    @Override
    public void teleportToPlayer(UUID uuid, UUID targetUuid) {
        teleports.add(uuid + "->" + targetUuid);
    }

    @Override
    public void createScoreboard(UUID uuid, ScoreboardContent content) {
        scoreboards.put(uuid, content);
    }

    @Override
    public void updateScoreboard(UUID uuid, ScoreboardContent content) {
        scoreboards.put(uuid, content);
    }

    @Override
    public void removeScoreboard(UUID uuid) {
        scoreboards.remove(uuid);
    }

    @Override
    public void discardScoreboard(UUID uuid) {
        scoreboards.remove(uuid);
    }

    @Override
    public void clearScoreboards() {
        scoreboards.clear();
    }

    @Override
    public void openTargetInventory(UUID viewerUuid, UUID targetUuid) {
        openedInventories.add(viewerUuid + "->" + targetUuid);
    }

    @Override
    public void runPlayerCommand(UUID uuid, String command) {
        commands.add(command);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        messages.computeIfAbsent(uuid, k -> new ArrayList<>()).add(message);
    }

    void connect(UUID uuid, String name) {
        online.add(uuid);
        names.put(uuid, name);
    }
}
