package gg.modl.minecraft.neoforge;

import gg.modl.minecraft.bridge.BridgePlayer;
import gg.modl.minecraft.bridge.BridgePlayerProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

public class NeoForgePlayerProvider implements BridgePlayerProvider {
    private final MinecraftServer server;

    public NeoForgePlayerProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public BridgePlayer getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? new NeoForgeBridgePlayer(player) : null;
    }

    @Override
    public Collection<BridgePlayer> getOnlinePlayers() {
        return server.getPlayerList().getPlayers().stream()
                .map(NeoForgeBridgePlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
}
