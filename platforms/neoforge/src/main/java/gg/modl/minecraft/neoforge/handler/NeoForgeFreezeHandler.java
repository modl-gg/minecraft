package gg.modl.minecraft.neoforge.handler;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NeoForgeFreezeHandler {
    private final MinecraftServer server;
    private final BridgeLocaleManager localeManager;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> frozenPositions = new ConcurrentHashMap<>();
    @Setter private NeoForgeStaffModeHandler staffModeHandler;
    @Setter private BridgeQueryClient bridgeClient;

    public NeoForgeFreezeHandler(MinecraftServer server, BridgeLocaleManager localeManager) {
        this.server = server;
        this.localeManager = localeManager;
    }

    public void freeze(String targetUuid, String staffUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.put(target, UUID.fromString(staffUuid));
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) {
            frozenPositions.put(target, new double[]{player.getX(), player.getY(), player.getZ()});
            player.sendSystemMessage(Component.literal(localeManager.getMessage("freeze.frozen")));
        }
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);
        frozenPositions.remove(target);
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) {
            player.sendSystemMessage(Component.literal(localeManager.getMessage("freeze.unfrozen")));
        }
    }

    public boolean isFrozen(UUID uuid) { return frozenPlayers.containsKey(uuid); }

    public void onTick() {
        if (frozenPositions.isEmpty()) return;
        frozenPositions.forEach((uuid, pos) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return;
            double dx = player.getX() - pos[0];
            double dy = player.getY() - pos[1];
            double dz = player.getZ() - pos[2];
            if (dx * dx + dy * dy + dz * dz > 0.01) {
                player.teleportTo(player.serverLevel(), pos[0], pos[1], pos[2],
                        java.util.Set.of(), player.getYRot(), player.getXRot(), false);
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        if (frozenPlayers.remove(uuid) == null) return;
        frozenPositions.remove(uuid);
        if (bridgeClient != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            String playerName = player != null ? player.getName().getString() : "Unknown";
            bridgeClient.sendMessage("FREEZE_LOGOUT", uuid.toString(), playerName);
        }
    }
}
