package gg.modl.minecraft.fabric.v26.handler;

import gg.modl.minecraft.bridge.locale.BridgeLocaleManager;
import gg.modl.minecraft.bridge.query.BridgeQueryClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FabricFreezeHandler {
    private final MinecraftServer server;
    private final BridgeLocaleManager localeManager;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, FreezeAnchor> frozenAnchors = new ConcurrentHashMap<>();
    private final Map<UUID, String> frozenPlayerNames = new ConcurrentHashMap<>();
    private FabricStaffModeHandler staffModeHandler;
    private BridgeQueryClient bridgeClient;

    private static final class FreezeAnchor {
        final ServerLevel world;
        final double x, y, z;
        final float yaw, pitch;

        FreezeAnchor(ServerLevel world, double x, double y, double z, float yaw, float pitch) {
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

        ServerPlayer player = server.getPlayerList().getPlayer(target);
        frozenPlayerNames.put(target, player != null ? player.getName().getString() : "Unknown");
        if (player != null) {
            frozenAnchors.put(target, new FreezeAnchor((ServerLevel) player.level(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
            player.sendSystemMessage(Component.literal(localeManager.getMessage("freeze.frozen")));
        }
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);
        frozenAnchors.remove(target);
        frozenPlayerNames.remove(target);

        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) {
            player.sendSystemMessage(Component.literal(localeManager.getMessage("freeze.unfrozen")));
        }
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    public void onTick() {
        if (frozenPlayers.isEmpty()) {
            return;
        }
        frozenPlayers.forEach((uuid, staff) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }

            FreezeAnchor anchor = frozenAnchors.get(uuid);
            if (anchor == null) {
                frozenAnchors.put(uuid, new FreezeAnchor((ServerLevel) player.level(),
                        player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
                frozenPlayerNames.put(uuid, player.getName().getString());
                player.sendSystemMessage(Component.literal(localeManager.getMessage("freeze.frozen")));
                return;
            }

            double dx = player.getX() - anchor.x;
            double dy = player.getY() - anchor.y;
            double dz = player.getZ() - anchor.z;
            boolean wrongWorld = (ServerLevel) player.level() != anchor.world;

            if (wrongWorld || dx * dx + dy * dy + dz * dz > 0.01) {
                player.teleportTo(anchor.world, anchor.x, anchor.y, anchor.z,
                        Set.<Relative>of(), anchor.yaw, anchor.pitch, false);
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        if (frozenPlayers.remove(uuid) == null) {
            return;
        }
        frozenAnchors.remove(uuid);

        String playerName = frozenPlayerNames.remove(uuid);
        if (bridgeClient != null) {
            bridgeClient.sendMessage("FREEZE_LOGOUT", uuid.toString(), playerName != null ? playerName : "Unknown");
        }
    }

    public void setStaffModeHandler(FabricStaffModeHandler staffModeHandler) {
        this.staffModeHandler = staffModeHandler;
    }

    public void setBridgeClient(BridgeQueryClient bridgeClient) {
        this.bridgeClient = bridgeClient;
    }
}
