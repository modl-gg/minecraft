package gg.modl.minecraft.fabric.v1_21_1;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import gg.modl.minecraft.core.chat.CommandInterceptService;
import gg.modl.minecraft.core.chat.CommandInterceptService.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class FabricCommandPacketListener extends PacketListenerAbstract {
    private final CommandInterceptService commandInterceptService;
    private final String serverName;
    private final MinecraftServer server;
    private final FabricTextSerializer textSerializer;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
            WrapperPlayClientChatCommand wrapper = new WrapperPlayClientChatCommand(event);
            handleCommand(event, wrapper.getCommand());
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
            WrapperPlayClientChatCommandUnsigned wrapper = new WrapperPlayClientChatCommandUnsigned(event);
            handleCommand(event, wrapper.getCommand());
        }
    }

    private void handleCommand(PacketReceiveEvent event, String command) {
        UUID uuid = resolvePlayerUuid(event);
        if (uuid == null) return;

        ServerPlayerEntity player = resolvePlayer(event, uuid);
        String username = player != null ? player.getName().getString() : "Unknown";

        CommandResult result = commandInterceptService.handleCommand(uuid, username, command, serverName);

        if (result == CommandResult.ALLOWED) return;

        event.setCancelled(true);

        String message = commandInterceptService.getBlockMessage(result, uuid);
        if (message != null && player != null) {
            textSerializer.sendInterceptMessage(player, message);
        }
    }

    private UUID resolvePlayerUuid(PacketReceiveEvent event) {
        if (event.getUser() != null) {
            return event.getUser().getUUID();
        }

        Object playerHandle = event.getPlayer();
        if (playerHandle instanceof ServerPlayerEntity player) {
            return player.getUuid();
        }

        log.warn("Ignoring {} because PacketEvents did not expose a Fabric player context", event.getPacketType());
        return null;
    }

    private ServerPlayerEntity resolvePlayer(PacketReceiveEvent event, UUID uuid) {
        Object playerHandle = event.getPlayer();
        if (playerHandle instanceof ServerPlayerEntity player) {
            return player;
        }
        return server.getPlayerManager().getPlayer(uuid);
    }

}
