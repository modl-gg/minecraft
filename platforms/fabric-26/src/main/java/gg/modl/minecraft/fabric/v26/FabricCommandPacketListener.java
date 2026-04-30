package gg.modl.minecraft.fabric.v26;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatCommandLogService;
import gg.modl.minecraft.core.service.FreezeService;
import gg.modl.minecraft.core.util.CommandInterceptHandler;
import gg.modl.minecraft.core.util.CommandInterceptHandler.CommandResult;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public class FabricCommandPacketListener extends PacketListenerAbstract {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FabricCommandPacketListener.class);

    private final Cache cache;
    private final FreezeService freezeService;
    private final ChatCommandLogService chatCommandLogService;
    private final LocaleManager localeManager;
    private final List<String> mutedCommands;
    private final String serverName;
    private final MinecraftServer server;

    public FabricCommandPacketListener(Cache cache, FreezeService freezeService,
                                       ChatCommandLogService chatCommandLogService, LocaleManager localeManager,
                                       List<String> mutedCommands, String serverName, MinecraftServer server) {
        this.cache = cache;
        this.freezeService = freezeService;
        this.chatCommandLogService = chatCommandLogService;
        this.localeManager = localeManager;
        this.mutedCommands = mutedCommands;
        this.serverName = serverName;
        this.server = server;
    }

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
        if (uuid == null) {
            return;
        }

        ServerPlayer player = resolvePlayer(event, uuid);
        String username = player != null ? player.getName().getString() : "Unknown";

        CommandResult result = CommandInterceptHandler.handleCommand(
                uuid, username, command, serverName,
                mutedCommands, cache, freezeService, chatCommandLogService);

        if (result == CommandResult.ALLOWED) {
            return;
        }

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
        if (playerHandle instanceof ServerPlayer player) {
            return player.getUUID();
        }

        log.warn("Ignoring {} because PacketEvents did not expose a Fabric player context", event.getPacketType());
        return null;
    }

    private ServerPlayer resolvePlayer(PacketReceiveEvent event, UUID uuid) {
        Object playerHandle = event.getPlayer();
        if (playerHandle instanceof ServerPlayer player) {
            return player;
        }
        return server.getPlayerList().getPlayer(uuid);
    }

    private void sendLegacyMessage(ServerPlayer player, String message) {
        try {
            String json = AdventureSerializer.toJson(CirrusChatElement.ofLegacyText(message).asComponent());
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            var component = ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
            if (component != null) {
                player.sendSystemMessage(component, false);
                return;
            }
        } catch (Exception ignored) {
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                message.replaceAll("\u00a7[0-9a-fk-or]", "")), false);
    }
}
