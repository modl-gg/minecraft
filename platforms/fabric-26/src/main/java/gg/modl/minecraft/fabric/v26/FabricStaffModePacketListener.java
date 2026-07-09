package gg.modl.minecraft.fabric.v26;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import gg.modl.minecraft.fabric.v26.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v26.handler.FabricStaffModeHandler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricStaffModePacketListener extends PacketListenerAbstract {
    private static final Logger log = LoggerFactory.getLogger(FabricStaffModePacketListener.class);

    private final FabricStaffModeHandler staffModeHandler;
    private final FabricFreezeHandler freezeHandler;

    public FabricStaffModePacketListener(FabricStaffModeHandler staffModeHandler, FabricFreezeHandler freezeHandler) {
        this.staffModeHandler = staffModeHandler;
        this.freezeHandler = freezeHandler;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            handleDigging(event);
        } else if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClickWindow(event);
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            handleInteractEntity(event);
        }
    }

    private void handleDigging(PacketReceiveEvent event) {
        WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = wrapper.getAction();
        if (action != DiggingAction.DROP_ITEM && action != DiggingAction.DROP_ITEM_STACK) {
            return;
        }

        UUID uuid = resolvePlayerUuid(event);
        if (uuid == null) {
            return;
        }
        if (staffModeHandler.isInStaffMode(uuid) || freezeHandler.isFrozen(uuid)) {
            event.setCancelled(true);
        }
    }

    private void handleClickWindow(PacketReceiveEvent event) {
        UUID uuid = resolvePlayerUuid(event);
        if (uuid == null || !staffModeHandler.isInStaffMode(uuid)) {
            return;
        }

        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        if (wrapper.getWindowId() == 0) {
            event.setCancelled(true);
        }
    }

    private void handleInteractEntity(PacketReceiveEvent event) {
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }

        UUID uuid = resolvePlayerUuid(event);
        if (uuid == null) {
            return;
        }
        if (staffModeHandler.isInStaffMode(uuid)) {
            event.setCancelled(true);
        }
    }

    private UUID resolvePlayerUuid(PacketReceiveEvent event) {
        if (event.getUser() != null) {
            return event.getUser().getUUID();
        }

        Object playerHandle = event.getPlayer();
        if (playerHandle instanceof ServerPlayer player) {
            return player.getUUID();
        }

        log.warn("Ignoring {} because PacketEvents did not expose a Fabric player context", event.getPacketType());
        return null;
    }
}
