package gg.modl.minecraft.fabric.v1_21_4.handler;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import lombok.Setter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Set;
import net.minecraft.server.world.ServerWorld;

public class FabricFreezeHandler {
    private final MinecraftServer server;
    private final BridgeLocaleManager localeManager;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeAnchor> frozenAnchors = new ConcurrentHashMap<>();
    private final Map<UUID, String> frozenPlayerNames = new ConcurrentHashMap<>();
    @Setter private FabricStaffModeHandler staffModeHandler;
    @Setter private BridgeQueryClient bridgeClient;

    private static final class FreezeAnchor {
        final ServerWorld world;
        final double x, y, z;
        final float yaw, pitch;

        FreezeAnchor(ServerWorld world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

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
            frozenAnchors.put(target, new FreezeAnchor((ServerWorld) player.getEntityWorld(),
                    player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
            player.sendMessage(Text.literal(localeManager.getMessage("freeze.frozen")));
        }
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);
        frozenAnchors.remove(target);
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
        if (frozenPlayers.isEmpty()) return;
        frozenPlayers.forEach((uuid, staff) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) return;

            FreezeAnchor anchor = frozenAnchors.get(uuid);
            if (anchor == null) {
                frozenAnchors.put(uuid, new FreezeAnchor((ServerWorld) player.getEntityWorld(),
                        player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
                frozenPlayerNames.put(uuid, player.getName().getString());
                player.sendMessage(Text.literal(localeManager.getMessage("freeze.frozen")));
                return;
            }

            double dx = player.getX() - anchor.x;
            double dy = player.getY() - anchor.y;
            double dz = player.getZ() - anchor.z;
            boolean wrongWorld = (ServerWorld) player.getEntityWorld() != anchor.world;

            if (wrongWorld || dx * dx + dy * dy + dz * dz > 0.01) {
                player.teleport(anchor.world, anchor.x, anchor.y, anchor.z,
                        Set.of(), anchor.yaw, anchor.pitch, false);
                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        if (frozenPlayers.remove(uuid) == null) return;
        frozenAnchors.remove(uuid);

        String playerName = frozenPlayerNames.remove(uuid);
        if (bridgeClient != null) {
            bridgeClient.sendMessage("FREEZE_LOGOUT", uuid.toString(), playerName != null ? playerName : "Unknown");
        }
    }
}
