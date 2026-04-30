package gg.modl.minecraft.fabric.v1_21_8.handler;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static gg.modl.minecraft.core.util.Java8Collections.*;

public class FabricFreezeHandler {
    private final MinecraftServer server;
    private final BridgeLocaleManager localeManager;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> frozenPositions = new ConcurrentHashMap<>();
    private final Map<UUID, String> frozenPlayerNames = new ConcurrentHashMap<>();
    @Setter private FabricStaffModeHandler staffModeHandler;
    @Setter private BridgeQueryClient bridgeClient;

    public FabricFreezeHandler(MinecraftServer server, BridgeLocaleManager localeManager) {
        this.server = server;
        this.localeManager = localeManager;
    }

    public void freeze(String targetUuid, String staffUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.put(target, UUID.fromString(staffUuid));

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(target);
        frozenPlayerNames.put(target, player != null ? player.getName().getString() : "Unknown");
        if (player != null) {
            frozenPositions.put(target, new double[]{player.getX(), player.getY(), player.getZ()});
            player.sendMessage(Text.literal(localeManager.getMessage("freeze.frozen")));
        }
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);
        frozenPositions.remove(target);
        frozenPlayerNames.remove(target);

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(target);
        if (player != null) {
            player.sendMessage(Text.literal(localeManager.getMessage("freeze.unfrozen")));
        }
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    public void onTick() {
        if (frozenPositions.isEmpty()) return;
        frozenPositions.forEach((uuid, pos) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) return;

            double dx = player.getX() - pos[0];
            double dy = player.getY() - pos[1];
            double dz = player.getZ() - pos[2];

            if (dx * dx + dy * dy + dz * dz > 0.01) {
                player.teleport((net.minecraft.server.world.ServerWorld) player.getWorld(), pos[0], pos[1], pos[2],
                        java.util.Set.of(), player.getYaw(), player.getPitch(), false);
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        if (frozenPlayers.remove(uuid) == null) return;
        frozenPositions.remove(uuid);

        if (bridgeClient != null) {
            String playerName = frozenPlayerNames.getOrDefault(uuid, "Unknown");
            frozenPlayerNames.remove(uuid);
            bridgeClient.sendMessage("FREEZE_LOGOUT", uuid.toString(), playerName);
        }
    }
}
