package gg.modl.minecraft.bridge;

import java.util.Collection;
import java.util.UUID;

public interface BridgePlayerProvider {

    BridgePlayer getPlayer(UUID uuid);

    Collection<BridgePlayer> getOnlinePlayers();

    boolean isOnline(UUID uuid);

    void dispatchConsoleCommand(String command);
}
