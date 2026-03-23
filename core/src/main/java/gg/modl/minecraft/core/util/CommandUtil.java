package gg.modl.minecraft.core.util;

import co.aikar.commands.CommandIssuer;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.PanelUnavailableException;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.cache.Cache;
import gg.modl.minecraft.core.locale.LocaleManager;

import java.util.Map;
import java.util.UUID;
import static gg.modl.minecraft.core.util.Java8Collections.*;

public final class CommandUtil {
    private CommandUtil() {}

    public static String resolveIssuerName(CommandIssuer sender, Cache cache, Platform platform) {
        if (!sender.isPlayer()) return "Console";
        String panelName = cache.getStaffDisplayName(sender.getUniqueId());
        if (panelName != null) return panelName;
        return platform.getAbstractPlayer(sender.getUniqueId(), false).getUsername();
    }

    public static String resolveIssuerId(CommandIssuer sender, Cache cache) {
        if (!sender.isPlayer()) return null;
        return cache.getStaffId(sender.getUniqueId());
    }

    public static String resolveSenderName(UUID uuid, Cache cache, Platform platform) {
        String name = cache.getStaffDisplayName(uuid);
        if (name != null) return name;
        AbstractPlayer player = platform.getPlayer(uuid);
        if (player != null) return player.getUsername();
        return "Staff";
    }

    public static Void handleApiError(CommandIssuer sender, Throwable throwable, LocaleManager localeManager) {
        return handleException(sender, throwable, localeManager, "general.punishment_error");
    }

    public static Void handleException(CommandIssuer sender, Throwable throwable, LocaleManager localeManager) {
        return handleException(sender, throwable, localeManager, "player_lookup.error");
    }

    public static Void handleException(CommandIssuer sender, Throwable throwable, LocaleManager localeManager, String errorKey) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (cause instanceof PanelUnavailableException) {
            sender.sendMessage(localeManager.getMessage("api_errors.panel_restarting"));
        } else {
            sender.sendMessage(localeManager.getMessage(errorKey,
                    mapOf("error", localeManager.sanitizeErrorMessage(cause.getMessage() != null ? cause.getMessage() : "Unknown error"))));
        }
        return null;
    }
}
