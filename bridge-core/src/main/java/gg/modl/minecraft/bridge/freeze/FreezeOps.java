package gg.modl.minecraft.bridge.freeze;

import java.util.UUID;

public interface FreezeOps {
    String playerName(UUID uuid);

    void sendMessage(UUID uuid, String message);

    void onFrozen(UUID target);

    void onUnfrozen(UUID target);
}
