package gg.modl.minecraft.bridge;

import java.util.UUID;

public interface BridgePlayer {

    UUID getUniqueId();

    String getName();

    void sendMessage(String message);

    boolean isOnline();

    int getEntityId();

    double getX();

    double getY();

    double getZ();

    float getYaw();

    float getPitch();

    String getWorldName();
}
