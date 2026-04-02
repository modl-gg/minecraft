package gg.modl.minecraft.core.util;

import gg.modl.proto.modl.v1.PlayerLoginResponse;
import gg.modl.proto.modl.v1.SimplePunishment;

public final class LoginResponseHelper {

    private final PlayerLoginResponse response;

    public LoginResponseHelper(PlayerLoginResponse response) {
        this.response = response;
    }

    public PlayerLoginResponse proto() {
        return response;
    }

    public boolean hasActiveBan() {
        return response.getActivePunishmentsList().stream()
                .anyMatch(p -> new PunishmentHelper(p).isBan());
    }

    public boolean hasActiveMute() {
        return response.getActivePunishmentsList().stream()
                .anyMatch(p -> new PunishmentHelper(p).isMute());
    }

    public SimplePunishment getActiveBan() {
        return response.getActivePunishmentsList().stream()
                .filter(p -> new PunishmentHelper(p).isBan())
                .findFirst()
                .orElse(null);
    }

    public SimplePunishment getActiveMute() {
        return response.getActivePunishmentsList().stream()
                .filter(p -> new PunishmentHelper(p).isMute())
                .findFirst()
                .orElse(null);
    }

    public boolean hasNotifications() {
        return response.getPendingNotificationsCount() > 0;
    }

    public boolean hasPendingStatWipes() {
        return response.getPendingStatWipesCount() > 0;
    }
}
