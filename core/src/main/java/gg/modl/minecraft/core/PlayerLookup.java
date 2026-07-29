package gg.modl.minecraft.core;

import gg.modl.minecraft.api.AbstractPlayer;

import java.util.Collection;
import java.util.UUID;

public interface PlayerLookup {
    boolean isOnline(UUID uuid);
    AbstractPlayer getAbstractPlayer(UUID uuid, boolean queryMojang);
    AbstractPlayer getAbstractPlayer(String username, boolean queryMojang);
    Collection<AbstractPlayer> getOnlinePlayers();
    AbstractPlayer getPlayer(UUID uuid);
}
