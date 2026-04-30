package gg.modl.minecraft.fabric.v1_21_1;

import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.CommandInterceptHandler;
import gg.modl.minecraft.core.util.CommandInterceptHandler.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class FabricCommandPacketListener extends PacketListenerAbstract {
    private final Cache cache;
    private final FreezeService freezeService;
    private final ChatCommandLogService chatCommandLogService;
    private final LocaleManager localeManager;
    private final List<String> mutedCommands;
    private final String serverName;
    private final MinecraftServer server;

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

        CommandResult result = CommandInterceptHandler.handleCommand(
                uuid, username, command, serverName,
                mutedCommands, cache, freezeService, chatCommandLogService);

        if (result == CommandResult.ALLOWED) return;

        event.setCancelled(true);

        String message = CommandInterceptHandler.getBlockMessage(result, uuid, cache, localeManager);
        if (message != null && player != null) {
            sendLegacyMessage(player, message);
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

    private void sendLegacyMessage(ServerPlayerEntity player, String message) {
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(message).asComponent());
            Text text = Text.Serialization.fromJson(json, player.getRegistryManager());
            if (text != null) {
                player.sendMessage(text, false);
                return;
            }
        } catch (Exception ignored) {
        }
        player.sendMessage(Text.literal(message.replaceAll("\u00a7[0-9a-fk-or]", "")), false);
    }
}
