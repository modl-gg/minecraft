package gg.modl.minecraft.fabric.v1_21_8;

import gg.modl.minecraft.bridge.BridgePlayer;
import gg.modl.minecraft.bridge.BridgePlayerProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class FabricPlayerProvider implements BridgePlayerProvider {
    private final MinecraftServer server;

    public FabricPlayerProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public BridgePlayer getPlayer(UUID uuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        return player != null ? new FabricBridgePlayer(player) : null;
    }

    @Override
    public Collection<BridgePlayer> getOnlinePlayers() {
        return server.getPlayerManager().getPlayerList().stream()
                .map(FabricBridgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid) != null;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        ServerCommandSource source = server.getCommandSource();
        server.getCommandManager().executeWithPrefix(source, command);
    }
}
