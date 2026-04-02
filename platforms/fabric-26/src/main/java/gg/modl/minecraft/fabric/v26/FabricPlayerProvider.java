package gg.modl.minecraft.fabric.v26;

import gg.modl.minecraft.bridge.BridgePlayer;
import gg.modl.minecraft.bridge.BridgePlayerProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? new FabricBridgePlayer(player) : null;
    }

    @Override
    public Collection<BridgePlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(FabricBridgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
    }
}
