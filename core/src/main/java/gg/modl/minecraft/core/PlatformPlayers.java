package gg.modl.minecraft.core;

import dev.simplix.cirrus.player.CirrusPlayerWrapper;
import gg.modl.minecraft.api.AbstractPlayer;

import java.util.UUID;

public interface PlatformPlayers extends PlayerLookup {
    CirrusPlayerWrapper getPlayerWrapper(UUID uuid);
    int getMaxPlayers();
    void kickPlayer(AbstractPlayer player, String reason);

    default String getPlayerSkinTexture(UUID uuid) { return null; }
}
