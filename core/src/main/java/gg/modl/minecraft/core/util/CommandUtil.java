package gg.modl.minecraft.core.util;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.impl.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;

public final class CommandUtil {

    private CommandUtil() {}

    public static String resolveIssuerName(CommandIssuer sender, Cache cache, Platform platform) {
        if (!sender.isPlayer()) return "Console";
        String panelName = cache.getStaffDisplayName(sender.getUniqueId());
        if (panelName != null) return panelName;
        return platform.getAbstractPlayer(sender.getUniqueId(), false).username();
    }

    public static String resolveSenderName(UUID uuid, Cache cache, Platform platform) {
        String name = cache.getStaffDisplayName(uuid);
        if (name != null) return name;
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player != null) return player.username();
        return "Staff";
    }

    public static Void handleApiError(CommandIssuer sender, Throwable throwable, LocaleManager localeManager) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        else {
            sender.sendMessage(localeManager.getPunishmentMessage("general.punishment_error",
                    Map.of("error", localeManager.sanitizeErrorMessage(cause.getMessage()))));
        }
        return null;
    }
}
