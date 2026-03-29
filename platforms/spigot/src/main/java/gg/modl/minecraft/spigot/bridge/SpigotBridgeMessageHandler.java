package gg.modl.minecraft.spigot.bridge;

import gg.modl.minecraft.bridge.BridgeScheduler;
import gg.modl.minecraft.bridge.query.BridgeMessageHandler;
import gg.modl.minecraft.bridge.statwipe.StatWipeHandler;
import gg.modl.minecraft.core.service.ReplayService;
import gg.modl.minecraft.spigot.bridge.handler.FreezeHandler;
import gg.modl.minecraft.spigot.bridge.handler.StaffModeHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class SpigotBridgeMessageHandler implements BridgeMessageHandler {
    private final JavaPlugin plugin;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final StatWipeHandler statWipeHandler;
    private final BridgeComponent bridgeComponent;
    private final BridgeScheduler scheduler;

    public SpigotBridgeMessageHandler(JavaPlugin plugin, FreezeHandler freezeHandler,
                                       StaffModeHandler staffModeHandler,
                                       StatWipeHandler statWipeHandler,
                                       BridgeComponent bridgeComponent,
                                       BridgeScheduler scheduler) {
        this.plugin = plugin;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.statWipeHandler = statWipeHandler;
        this.bridgeComponent = bridgeComponent;
        this.scheduler = scheduler;
    }

    @Override
    public void onFreeze(String targetUuid, String staffUuid) {
        freezeHandler.freeze(targetUuid, staffUuid);
    }

    @Override
    public void onUnfreeze(String targetUuid) {
        freezeHandler.unfreeze(targetUuid);
    }

    @Override
    public void onStaffModeEnter(String staffUuid, String staffName) {
        staffModeHandler.enterStaffMode(staffUuid);
    }

    @Override
    public void onStaffModeExit(String staffUuid, String staffName) {
        staffModeHandler.exitStaffMode(staffUuid);
    }

    @Override
    public void onVanishEnter(String staffUuid, String staffName) {
        staffModeHandler.vanishFromBridge(staffUuid);
    }

    @Override
    public void onVanishExit(String staffUuid, String staffName) {
        staffModeHandler.unvanishFromBridge(staffUuid);
    }

    @Override
    public void onTargetRequest(String staffUuid, String targetUuid) {
        UUID targetUuidParsed = UUID.fromString(targetUuid);
        scheduler.runForPlayer(targetUuidParsed, () -> {
            Player target = Bukkit.getPlayer(targetUuidParsed);
            if (target == null || !target.isOnline()) return;

            bridgeComponent.getBridgeClient().sendMessage("TARGET_RESPONSE", staffUuid, targetUuid,
                    bridgeComponent.getBridgeConfig().getServerName());
            staffModeHandler.setTarget(staffUuid, targetUuid);
        });
    }

    @Override
    public void onStatWipe(String username, String uuid, String punishmentId) {
        plugin.getLogger().info("[bridge] Processing stat-wipe for " + username + " (punishment: " + punishmentId + ")");
        scheduler.runSync(() -> {
            boolean success = statWipeHandler.execute(username, uuid, punishmentId);
            plugin.getLogger().info("[bridge] Stat-wipe for " + username + " " +
                    (success ? "succeeded" : "failed") + " (punishment: " + punishmentId + ")");
        });
    }

    @Override
    public void onCaptureReplay(String targetUuid, String targetName) {
        UUID uuid = UUID.fromString(targetUuid);
        scheduler.runForPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            ReplayService replayService = bridgeComponent.getReplayService();
            if (replayService == null) {
                bridgeComponent.getBridgeClient().sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
                return;
            }

            replayService.captureReplay(uuid, targetName)
                    .thenAccept(replayId -> bridgeComponent.getBridgeClient()
                            .sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, replayId != null ? replayId : ""))
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("[bridge] CAPTURE_REPLAY failed for " + targetName + ": " + ex.getMessage());
                        bridgeComponent.getBridgeClient().sendMessage("CAPTURE_REPLAY_RESPONSE", targetUuid, "");
                        return null;
                    });
        });
    }

    @Override
    public void onPanelUrl(String panelUrl) {
        // handled by BridgeQueryClient directly
    }
}
