package gg.modl.minecraft.fabric.v1_21_11;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import gg.modl.minecraft.fabric.v1_21_11.handler.FabricFreezeHandler;
import gg.modl.minecraft.fabric.v1_21_11.handler.FabricStaffModeHandler;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class FabricStaffModePacketListener extends PacketListenerAbstract {
    private final FabricStaffModeHandler staffModeHandler;
    private final FabricFreezeHandler freezeHandler;

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
        if (action != DiggingAction.DROP_ITEM && action != DiggingAction.DROP_ITEM_STACK) return;

        UUID uuid = event.getUser().getUUID();
        if (staffModeHandler.isInStaffMode(uuid) || freezeHandler.isFrozen(uuid)) {
            event.setCancelled(true);
        }
    }

    private void handleClickWindow(PacketReceiveEvent event) {
        UUID uuid = event.getUser().getUUID();
        if (!staffModeHandler.isInStaffMode(uuid)) return;

        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        if (wrapper.getWindowId() == 0) {
            // Block clicks in the player's own inventory only.
            // Allow clicks in opened screens (windowId > 0): Cirrus menus, target inventory, silent containers.
            event.setCancelled(true);
        }
    }

    private void handleInteractEntity(PacketReceiveEvent event) {
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        UUID uuid = event.getUser().getUUID();
        if (staffModeHandler.isInStaffMode(uuid)) {
            event.setCancelled(true);
        }
    }
}
